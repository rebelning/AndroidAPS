package app.aaps.plugins.wtconnect.sync

import app.aaps.core.interfaces.auth.AapsAuthSessionProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.wtconnect.api.WTAlarmSync
import app.aaps.plugins.wtconnect.api.WTBasalHistory
import app.aaps.plugins.wtconnect.api.WTBasalProfile
import app.aaps.plugins.wtconnect.api.WTBolusRecord
import app.aaps.plugins.wtconnect.api.WTConnectApi
import app.aaps.plugins.wtconnect.api.WTConnectEndpoints
import app.aaps.plugins.wtconnect.api.WTGlucoseData
import app.aaps.plugins.wtconnect.api.WTPumpStatus
import app.aaps.plugins.wtconnect.api.WTTempBasalSync
import app.aaps.plugins.wtconnect.database.WTConnectDao
import app.aaps.plugins.wtconnect.database.WTConnectOutboxEntry
import app.aaps.plugins.wtconnect.status.WTConnectLogLevel
import app.aaps.plugins.wtconnect.status.WTConnectStatusStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class WTConnectHttpSync @Inject constructor(
    private val api: WTConnectApi,
    private val gson: Gson,
    private val dao: WTConnectDao,
    private val authSessionProvider: AapsAuthSessionProvider,
    private val statusStore: WTConnectStatusStore,
    private val aapsLogger: AAPSLogger
) {
    @Volatile private var executor: ScheduledExecutorService? = null

    @Synchronized
    fun start() {
        if (executor?.isShutdown == false) return
        executor = Executors.newSingleThreadScheduledExecutor().also {
            it.execute { statusStore.updatePendingHttpCount(dao.outboxCount()) }
            it.scheduleWithFixedDelay(::flushSafely, 1, 30, TimeUnit.SECONDS)
        }
        aapsLogger.info(LTag.WTCONNECT, "HTTP sync outbox started")
        statusStore.record("HTTP sync service started")
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
        aapsLogger.info(LTag.WTCONNECT, "HTTP sync outbox stopped")
        statusStore.record("HTTP sync service stopped")
    }

    fun syncBolusRecord(value: WTBolusRecord) = enqueue(WTConnectEndpoints.BOLUS_RECORD, value.recordNo, value)
    fun syncPumpStatus(value: WTPumpStatus) = enqueue(WTConnectEndpoints.PUMP_STATUS, "status-${System.currentTimeMillis()}", value)
    fun syncGlucoseData(value: WTGlucoseData) {
        val point = value.termData.maxByOrNull { it.valueIndex.toLongOrNull() ?: 0L }
        enqueue(WTConnectEndpoints.GLUCOSE_DATA, "${value.termInfo.probeNo}-${point?.valueIndex}", value)
    }
    fun syncBasalProfile(value: WTBasalProfile) = enqueue(WTConnectEndpoints.BASAL_PROFILE, "profile", value)
    fun syncBasalHistory(value: WTBasalHistory) = enqueue(WTConnectEndpoints.BASAL_HISTORY, "${value.recordDate}-${value.deviceRecordTime}", value)
    fun syncAlarm(value: WTAlarmSync) {
        val alarm = value.alarmList.firstOrNull()
        enqueue(WTConnectEndpoints.ALARM, "${alarm?.alarmType}-${alarm?.alarmTime}", value)
    }
    fun syncTempBasal(value: WTTempBasalSync) {
        value.records.forEach { record ->
            enqueue(WTConnectEndpoints.TEMP_BASAL, record.recordNo, WTTempBasalSync(value.familyPhone, listOf(record)))
        }
    }

    private fun enqueue(endpoint: String, businessKey: String, payload: Any) {
        val worker = executor ?: return
        val body = JsonObject().apply { add("params", gson.toJsonTree(payload)) }
        worker.execute {
            val entryId = dao.putOutbox(
                WTConnectOutboxEntry(
                    endpoint = endpoint,
                    businessKey = businessKey,
                    payloadJson = gson.toJson(body)
                )
            )
            statusStore.updatePendingHttpCount(dao.outboxCount())
            statusStore.record("HTTP queued: ${endpoint.displayName()}")
            aapsLogger.debug(LTag.WTCONNECT, "HTTP sync queued endpoint=$endpoint entryId=$entryId")
            flushSafely()
        }
    }

    private fun flushSafely() {
        try {
            flush()
        } catch (error: Throwable) {
            aapsLogger.error(LTag.WTCONNECT, "HTTP outbox flush failed", error)
            statusStore.record(
                "HTTP outbox failed: ${error.message ?: error.javaClass.simpleName}",
                WTConnectLogLevel.ERROR
            )
        }
    }

    private fun flush() {
        val session = authSessionProvider.currentSession() ?: return
        dao.pendingOutbox(System.currentTimeMillis()).forEach { entry ->
            val body = gson.fromJson(entry.payloadJson, JsonObject::class.java)
            try {
                val response = api.sync(entry.endpoint, authSessionProvider.createAppToken(), body).execute()
                if (response.isSuccessful && response.body()?.successful == true) {
                    dao.deleteOutbox(entry.id)
                    statusStore.updatePendingHttpCount(dao.outboxCount())
                    statusStore.record("HTTP completed: ${entry.endpoint.displayName()}")
                    aapsLogger.debug(LTag.WTCONNECT, "HTTP sync completed endpoint=${entry.endpoint}")
                } else {
                    val reason = response.body()?.errorNo ?: "HTTP ${response.code()}"
                    aapsLogger.warn(LTag.WTCONNECT, "HTTP sync rejected endpoint=${entry.endpoint} reason=$reason")
                    statusStore.record(
                        "HTTP rejected: ${entry.endpoint.displayName()} ($reason)",
                        WTConnectLogLevel.WARNING
                    )
                    scheduleRetry(entry)
                }
            } catch (error: Exception) {
                aapsLogger.warn(LTag.WTCONNECT, "HTTP sync deferred endpoint=${entry.endpoint}: ${error.message}")
                statusStore.record(
                    "HTTP deferred: ${entry.endpoint.displayName()} (${error.message ?: "network error"})",
                    WTConnectLogLevel.WARNING
                )
                scheduleRetry(entry)
            }
        }
    }

    private fun scheduleRetry(entry: WTConnectOutboxEntry) {
        val attempts = entry.attempts + 1
        val delaySeconds = min(15L * (1L shl min(attempts, 6)), 15L * 60L)
        dao.retryOutbox(entry.id, attempts, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds))
        aapsLogger.debug(
            LTag.WTCONNECT,
            "HTTP sync retry scheduled endpoint=${entry.endpoint} attempt=$attempts delaySeconds=$delaySeconds"
        )
    }

    private fun String.displayName(): String = substringAfterLast('/')
}
