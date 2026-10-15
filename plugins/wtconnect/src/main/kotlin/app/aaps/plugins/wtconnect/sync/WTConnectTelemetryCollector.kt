package app.aaps.plugins.wtconnect.sync

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.auth.AapsAuthSessionProvider
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.plugins.wtconnect.api.WTAlarm
import app.aaps.plugins.wtconnect.api.WTAlarmSync
import app.aaps.plugins.wtconnect.api.WTBasalHistory
import app.aaps.plugins.wtconnect.api.WTBasalProfile
import app.aaps.plugins.wtconnect.api.WTBasalRate
import app.aaps.plugins.wtconnect.api.WTBasalSegment
import app.aaps.plugins.wtconnect.api.WTBolusRecord
import app.aaps.plugins.wtconnect.api.WTGlucoseData
import app.aaps.plugins.wtconnect.api.WTGlucosePoint
import app.aaps.plugins.wtconnect.api.WTPumpStatus
import app.aaps.plugins.wtconnect.api.WTTempBasalRecord
import app.aaps.plugins.wtconnect.api.WTTempBasalSync
import app.aaps.plugins.wtconnect.api.WTTermInfo
import app.aaps.plugins.wtconnect.websocket.WTConnectWebSocketManager
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WTConnectTelemetryCollector @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val iobCobCalculator: IobCobCalculator,
    private val constraintsChecker: ConstraintsChecker,
    private val authSessionProvider: AapsAuthSessionProvider,
    private val socketManager: WTConnectWebSocketManager,
    private val httpSync: WTConnectHttpSync,
    private val aapsLogger: AAPSLogger
) {
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    private var lastBasalHistoryDispatchKey: String? = null

    fun syncLatestGlucose() {
        val session = authSessionProvider.currentSession() ?: return
        val glucose = persistenceLayer.getLastGlucoseValue() ?: return
        val wsPayload = glucosePayload(glucose, null, session.pumpCode)
        val sentByWebSocket = socketManager.connected && socketManager.sendGlucose(wsPayload)
        if (!sentByWebSocket) {
            httpSync.syncGlucoseData(glucosePayload(glucose, session.phoneNumber, session.pumpCode))
        }
        aapsLogger.debug(
            LTag.WTCONNECT,
            "Glucose sync dispatched channel=${if (sentByWebSocket) "websocket" else "http"} valueIndex=${glucose.timestamp}"
        )
    }

    fun syncPumpStatus() {
        val session = authSessionProvider.currentSession() ?: return
        val pump = activePlugin.activePump
        if (pump.model() != session.pumpType) return
        val now = System.currentTimeMillis()
        val temporaryBasal = persistenceLayer.getTemporaryBasalActiveAt(now)
        val tdd = persistenceLayer.getCalculatedTotalDailyDose(now)
        httpSync.syncPumpStatus(
            WTPumpStatus(
                familyPhone = session.phoneNumber,
                deviceModel = pump.model().description,
                deviceNo = session.pumpCode,
                deviceRunningStatus = if (pump.isSuspended()) 2 else 1,
                battery = pump.batteryLevel,
                residueAmount = pump.reservoirLevel.takeIf { it.isFinite() && it >= 0.0 },
                dayTotalAmount = tdd?.let { if (it.totalAmount > 0) it.totalAmount else it.basalAmount + it.bolusAmount },
                bolusMax = constraintsChecker.getMaxBolusAllowed().value(),
                basalMax = pump.pumpDescription.basalMaximumRate,
                currentBasal = pump.baseBasalRate,
                tempBasal = temporaryBasal?.rate,
                tempBasalType = temporaryBasal?.let { if (it.isAbsolute) 2 else 1 },
                syncTime = format(now)
            )
        )
        aapsLogger.debug(LTag.WTCONNECT, "Pump status sync queued pumpType=${session.pumpType}")
    }

    fun syncLatestBolus() {
        val session = authSessionProvider.currentSession() ?: return
        val bolus = persistenceLayer.getNewestBolus() ?: return
        if (!bolus.isValid || bolus.notes?.startsWith("WTConnect ") == true) return
        val recordNo = bolus.ids.pumpId?.let { "R$it" } ?: "R${bolus.timestamp}-${bolus.id}"
        httpSync.syncBolusRecord(
            WTBolusRecord(
                recordNo = recordNo,
                familyPhone = session.phoneNumber,
                bolusType = 1,
                targetAmount = bolus.amount,
                finishAmount = bolus.amount,
                infuseStartTime = format(bolus.timestamp),
                infuseEndTime = format(bolus.timestamp)
            )
        )
        aapsLogger.debug(LTag.WTCONNECT, "Local bolus sync queued recordNo=$recordNo")
    }

    fun syncBasalProfile() {
        val session = authSessionProvider.currentSession() ?: return
        val profile = profileFunction.getProfile() ?: return
        val rates = (0 until 24).map { hour ->
            WTBasalRate(startMinute = hour * 60, rate = profile.getBasalTimeFromMidnight(hour * 3600))
        }
        httpSync.syncBasalProfile(WTBasalProfile(session.phoneNumber, format(System.currentTimeMillis()), rates))
        aapsLogger.debug(LTag.WTCONNECT, "Basal profile sync queued segmentCount=${rates.size}")
    }

    /**
     * Uploads the complete list of finished hourly basal segments for today. The amount is
     * calculated with the same five-minute effective-basal sampling used by AAPS TDD, so temp
     * basal changes are included and an incomplete current hour is never reported.
     */
    @Synchronized
    fun syncBasalHistory() {
        val session = authSessionProvider.currentSession() ?: return
        val now = ZonedDateTime.now()
        // At midnight the just-finished hour belongs to yesterday. Upload yesterday's complete
        // 24-hour snapshot before switching to the new day's first segment at 01:00.
        val historyDate = if (now.hour == 0) now.toLocalDate().minusDays(1) else now.toLocalDate()
        val completedHours = if (now.hour == 0) 24 else now.hour
        val recordDate = historyDate.toString()
        val dispatchKey = "${session.pumpCode}|$recordDate|$completedHours"
        if (lastBasalHistoryDispatchKey == dispatchKey) return

        val startOfDay = historyDate.atStartOfDay(now.zone)
        val segments = (0 until completedHours).map { hour ->
            var amount = 0.0
            repeat(SAMPLES_PER_HOUR) { sample ->
                val timestamp = startOfDay.plusMinutes((hour * 60L) + (sample * SAMPLE_MINUTES)).toInstant().toEpochMilli()
                val profile = profileFunction.getProfile(timestamp) ?: return
                amount += iobCobCalculator.getBasalData(profile, timestamp).tempBasalAbsolute / SAMPLES_PER_HOUR
            }
            WTBasalSegment(startMinute = hour * 60, amount = amount)
        }
        httpSync.syncBasalHistory(
            WTBasalHistory(
                familyPhone = session.phoneNumber,
                recordDate = recordDate,
                deviceRecordTime = format(System.currentTimeMillis()),
                segmentList = segments
            )
        )
        aapsLogger.debug(
            LTag.WTCONNECT,
            "Basal history sync queued recordDate=$recordDate segmentCount=${segments.size}"
        )
        // A process restart may safely resend the same full-day snapshot; server-side handling is
        // idempotent and the HTTP outbox keeps retries of this process durable.
        lastBasalHistoryDispatchKey = dispatchKey
    }

    fun syncLatestTempBasal() {
        val session = authSessionProvider.currentSession() ?: return
        val from = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        val basal = persistenceLayer.getTemporaryBasalsStartingFromTime(from, false).blockingGet().firstOrNull() ?: return
        if (!basal.isValid) return
        val now = System.currentTimeMillis()
        val elapsed = (now.coerceAtMost(basal.end) - basal.timestamp).coerceAtLeast(0)
        val value = if (basal.isAbsolute) basal.rate * basal.duration / TimeUnit.HOURS.toMillis(1).toDouble() else basal.rate
        httpSync.syncTempBasal(
            WTTempBasalSync(
                familyPhone = session.phoneNumber,
                records = listOf(
                    WTTempBasalRecord(
                        recordNo = basal.ids.pumpId?.let { "TB$it" } ?: "TB${basal.timestamp}-${basal.id}",
                        tempBasalType = if (basal.isAbsolute) 2 else 1,
                        tempBasalValue = value,
                        startTime = format(basal.timestamp),
                        endTime = format(basal.end),
                        totalTime = TimeUnit.MILLISECONDS.toMinutes(basal.duration).toInt(),
                        runTime = TimeUnit.MILLISECONDS.toMinutes(elapsed).toInt(),
                        status = if (basal.isInProgress) 2 else 1
                    )
                )
            )
        )
        aapsLogger.debug(
            LTag.WTCONNECT,
            "Temporary basal sync queued recordNo=${basal.ids.pumpId?.let { "TB$it" } ?: "local"} status=${if (basal.isInProgress) 2 else 1}"
        )
    }

    fun syncAlarm(notification: Notification) {
        val session = authSessionProvider.currentSession() ?: return
        val alarmType = when (notification.id) {
            Notification.PUMP_SUSPENDED -> 6
            Notification.PUMP_UNREACHABLE,
            Notification.BLUETOOTH_NOT_ENABLED,
            Notification.PUMP_SYNC_ERROR,
            Notification.RILEYLINK_CONNECTION -> 14
            else -> return
        }
        httpSync.syncAlarm(
            WTAlarmSync(
                session.phoneNumber,
                listOf(WTAlarm(alarmType, format(notification.date.takeIf { it > 0 } ?: System.currentTimeMillis()), notification.text))
            )
        )
        aapsLogger.info(LTag.WTCONNECT, "Alarm sync queued notificationId=${notification.id} alarmType=$alarmType")
    }

    private fun glucosePayload(glucose: GV, familyPhone: String?, pumpCode: String): WTGlucoseData =
        WTGlucoseData(
            familyPhone = familyPhone,
            termInfo = WTTermInfo(
                probeNo = "AAPS-${glucose.sourceSensor.name}",
                termNo = pumpCode
            ),
            termData = listOf(
                WTGlucosePoint(
                    value = String.format(Locale.US, "%.1f", glucose.value / 18.0),
                    valueIndex = glucose.timestamp.toString(),
                    recordTime = format(glucose.timestamp),
                    arrowState = null
                )
            )
        )

    private fun format(timestamp: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(timestamp))

    companion object {
        private const val SAMPLE_MINUTES = 5L
        private const val SAMPLES_PER_HOUR = 12
    }
}
