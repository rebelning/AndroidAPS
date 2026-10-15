package app.aaps.plugins.wtconnect.command

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.auth.AapsAuthSessionProvider
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.wtconnect.R
import app.aaps.plugins.wtconnect.api.WTBolusInfuseCommand
import app.aaps.plugins.wtconnect.api.WTBolusProgress
import app.aaps.plugins.wtconnect.api.WTBolusRecord
import app.aaps.plugins.wtconnect.api.WTBolusStopCommand
import app.aaps.plugins.wtconnect.api.WTCommandReject
import app.aaps.plugins.wtconnect.api.WTInfuseState
import app.aaps.plugins.wtconnect.api.WTInfuseStatus
import app.aaps.plugins.wtconnect.api.WTMessageType
import app.aaps.plugins.wtconnect.database.WTConnectDao
import app.aaps.plugins.wtconnect.database.WTConnectOperation
import app.aaps.plugins.wtconnect.status.WTConnectLogLevel
import app.aaps.plugins.wtconnect.status.WTConnectStatusStore
import app.aaps.plugins.wtconnect.sync.WTConnectHttpSync
import app.aaps.plugins.wtconnect.websocket.WTConnectWebSocketManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class WTConnectCommandDispatcher @Inject constructor(
    private val gson: Gson,
    private val sp: SP,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val constraintsChecker: ConstraintsChecker,
    private val authSessionProvider: AapsAuthSessionProvider,
    private val dao: WTConnectDao,
    private val socketManager: WTConnectWebSocketManager,
    private val httpSync: WTConnectHttpSync,
    private val statusStore: WTConnectStatusStore,
    private val userEntryLogger: UserEntryLogger,
    private val aapsLogger: AAPSLogger
) {
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun handle(rawMessage: String) {
        val envelope = try {
            gson.fromJson(rawMessage, JsonObject::class.java)
        } catch (error: Exception) {
            aapsLogger.warn(LTag.WTCONNECT, "Ignoring malformed WebSocket message")
            statusStore.record("Malformed WebSocket command ignored", WTConnectLogLevel.WARNING)
            return
        }
        val type = envelope.get("type")?.asString ?: return
        if (type.equals("pong", true)) return
        val data = envelope.getAsJsonObject("data") ?: JsonObject()
        val msgId = envelope.get("msgId")?.asString.orEmpty()
        aapsLogger.info(LTag.WTCONNECT, "Remote command received type=$type hasMsgId=${msgId.isNotBlank()}")
        statusStore.record("Remote command received: $type")
        when (type) {
            WTMessageType.BOLUS_INFUSE -> handleBolus(msgId, data)
            WTMessageType.BOLUS_STOP -> handleStop(data)
            WTMessageType.PUMP_PAUSE,
            WTMessageType.PUMP_RESUME,
            WTMessageType.LOOP_CLOSE,
            WTMessageType.LOOP_START -> reject(data.string("operationNo"), "WTConnect does not enable this command yet")
            else -> aapsLogger.debug(LTag.WTCONNECT, "Ignoring unsupported message type=$type")
        }
    }

    fun onBolusProgress(event: EventOverviewBolusProgress) {
        val treatmentId = event.id ?: event.t.id ?: return
        val operation = dao.operationByTreatmentId(treatmentId) ?: return
        if (operation.state == WTInfuseState.COMPLETED || operation.state == WTInfuseState.STOPPED) return
        if (event.percent < 0) return
        val finishAmount = (operation.targetAmount * event.percent.coerceIn(0, 100) / 100.0)
        dao.updateOperation(operation.operationNo, finishAmount, WTInfuseState.STARTED, null)
        val sent = socketManager.send(
            WTMessageType.BOLUS_PROGRESS,
            operation.operationNo,
            WTBolusProgress(operation.operationNo, operation.recordNo, finishAmount)
        )
        aapsLogger.debug(
            LTag.WTCONNECT,
            "Remote bolus progress operationNo=${operation.operationNo} percent=${event.percent} wsQueued=$sent"
        )
        statusStore.record("Bolus progress ${event.percent.coerceIn(0, 100)}% (${operation.recordNo})")
    }

    private fun handleBolus(msgId: String, data: JsonObject) {
        val command = try {
            gson.fromJson(data, WTBolusInfuseCommand::class.java)
        } catch (error: Exception) {
            reject(data.string("operationNo"), "invalid bolus command")
            return
        }
        dao.operation(command.operationNo)?.let { existing ->
            aapsLogger.info(LTag.WTCONNECT, "Replaying existing remote bolus operationNo=${command.operationNo} state=${existing.state}")
            replayExisting(existing)
            return
        }
        val rejection = validateBolus(command)
        if (rejection != null) {
            reject(command.operationNo, rejection)
            return
        }

        val session = authSessionProvider.currentSession() ?: run {
            reject(command.operationNo, "authorization is unavailable")
            return
        }
        val info = DetailedBolusInfo().apply {
            insulin = command.targetAmount
            bolusType = BS.Type.NORMAL
            notes = "WTConnect ${command.operationNo}"
        }
        val recordNo = "R${info.id}"
        val operation = WTConnectOperation(
            operationNo = command.operationNo,
            msgId = msgId,
            recordNo = recordNo,
            treatmentId = info.id,
            pumpType = session.pumpType.name,
            pumpCode = session.pumpCode,
            bolusType = command.bolusType,
            targetAmount = command.targetAmount,
            state = WTInfuseState.SENT,
            startedAt = System.currentTimeMillis()
        )
        if (dao.insertOperation(operation) == -1L) {
            aapsLogger.info(LTag.WTCONNECT, "Remote bolus insert raced with existing operationNo=${command.operationNo}")
            dao.operation(command.operationNo)?.let(::replayExisting)
            return
        }
        aapsLogger.info(
            LTag.WTCONNECT,
            "Remote bolus accepted operationNo=${command.operationNo} pumpType=${session.pumpType}"
        )
        statusStore.record("Remote bolus accepted (${operation.recordNo})")

        userEntryLogger.log(
            action = Action.BOLUS,
            source = Sources.Pump,
            note = "Remote operation ${command.operationNo}",
            listValues = listOf(ValueWithUnit.Insulin(command.targetAmount))
        )
        sendStatus(operation, WTInfuseState.SENT)
        val queued = commandQueue.bolus(info, object : Callback() {
            override fun run() {
                val current = dao.operation(operation.operationNo) ?: return
                // A successful remote stop is followed by the normal bolus callback on some
                // pump drivers. Keep the terminal STOPPED state instead of reporting a false
                // rejection after cancelAllBoluses().
                if (current.state == WTInfuseState.STOPPED) return
                val delivered = maxOf(result.bolusDelivered.coerceAtLeast(0.0), current.finishAmount)
                if (result.success) {
                    val completed = current.copy(finishAmount = delivered, state = WTInfuseState.COMPLETED, finishedAt = System.currentTimeMillis())
                    dao.updateOperation(completed.operationNo, delivered, completed.state, completed.finishedAt)
                    sendStatus(completed, WTInfuseState.COMPLETED)
                    syncCompletedBolus(completed)
                    aapsLogger.info(
                        LTag.WTCONNECT,
                        "Remote bolus completed operationNo=${completed.operationNo} recordNo=${completed.recordNo}"
                    )
                    statusStore.record("Remote bolus completed (${completed.recordNo})")
                } else {
                    dao.updateOperation(operation.operationNo, delivered, REJECTED, System.currentTimeMillis())
                    reject(operation.operationNo, result.comment.ifBlank { "pump rejected bolus" })
                }
            }
        })
        if (queued) {
            dao.updateOperation(operation.operationNo, 0.0, WTInfuseState.STARTED, null)
            sendStatus(operation.copy(state = WTInfuseState.STARTED), WTInfuseState.STARTED)
            aapsLogger.info(LTag.WTCONNECT, "Remote bolus queued operationNo=${operation.operationNo}")
            statusStore.record("Remote bolus queued (${operation.recordNo})")
        } else {
            dao.updateOperation(operation.operationNo, 0.0, REJECTED, System.currentTimeMillis())
            reject(operation.operationNo, "bolus could not be queued")
        }
    }

    private fun handleStop(data: JsonObject) {
        val command = try {
            gson.fromJson(data, WTBolusStopCommand::class.java)
        } catch (error: Exception) {
            reject(data.string("operationNo"), "invalid stop command")
            return
        }
        aapsLogger.info(
            LTag.WTCONNECT,
            "Remote bolus stop received operationNo=${command.operationNo} targetOperationNo=${command.targetOperationNo}"
        )
        statusStore.record("Remote bolus stop command received")
        if (!remoteControlEnabled()) {
            reject(command.operationNo, "remote pump commands are disabled")
            return
        }
        val target = dao.operation(command.targetOperationNo)
        if (target == null) {
            reject(command.operationNo, "target operation was not found")
            return
        }
        val session = authSessionProvider.currentSession()
        val identityMatches = session != null && target.pumpType == session.pumpType.name && target.pumpCode == session.pumpCode
        val recordMatches = command.recordNo.isNullOrBlank() || command.recordNo == target.recordNo
        val treatmentMatches = BolusProgressData.id == target.treatmentId && commandQueue.bolusInQueue()
        if (!identityMatches || !recordMatches || !treatmentMatches || target.state != WTInfuseState.STARTED) {
            reject(command.operationNo, "stop command does not match the active WTConnect bolus")
            return
        }
        commandQueue.cancelAllBoluses(target.treatmentId)
        val stopped = target.copy(
            finishAmount = BolusProgressData.percent.coerceIn(0, 100) * target.targetAmount / 100.0,
            state = WTInfuseState.STOPPED,
            finishedAt = System.currentTimeMillis()
        )
        dao.updateOperation(stopped.operationNo, stopped.finishAmount, stopped.state, stopped.finishedAt)
        userEntryLogger.log(Action.CANCEL_BOLUS, Sources.Pump, "WTConnect remote stop ${command.operationNo}")
        sendStatus(stopped, WTInfuseState.STOPPED)
        syncCompletedBolus(stopped)
        aapsLogger.info(
            LTag.WTCONNECT,
            "Remote bolus stop submitted targetOperationNo=${stopped.operationNo} recordNo=${stopped.recordNo}"
        )
        statusStore.record("Remote bolus stop submitted (${stopped.recordNo})")
    }

    private fun validateBolus(command: WTBolusInfuseCommand): String? {
        if (!remoteControlEnabled()) return "remote pump commands are disabled"
        if (command.bolusType != 1) return "only normal bolus is supported"
        if (!command.targetAmount.isFinite() || command.targetAmount <= 0.0) return "invalid bolus amount"
        val session = authSessionProvider.currentSession() ?: return "authorization is unavailable"
        if (session.pumpType !in SUPPORTED_PUMPS) return "active pump is not supported by WTConnect"
        val pump = activePlugin.activePump
        if (pump.model() != session.pumpType || pump.isSuspended()) return "active pump identity or state is invalid"
        if (!pump.isInitialized() || !pump.isConnected() || pump.isBusy()) return "pump is not ready"
        if (commandQueue.bolusInQueue()) return "another bolus is active"
        val constrained = constraintsChecker.applyBolusConstraints(ConstraintObject(command.targetAmount, aapsLogger)).value()
        if (abs(constrained - command.targetAmount) > 0.0001) return "bolus amount exceeds local safety constraints"
        return null
    }

    private fun sendStatus(operation: WTConnectOperation, state: Int) {
        val sent = socketManager.send(
            WTMessageType.INFUSE_STATUS,
            operation.operationNo,
            WTInfuseStatus(
                operationNo = operation.operationNo,
                recordNo = operation.recordNo,
                infuseStatus = state.coerceIn(WTInfuseState.SENT, WTInfuseState.STOPPED),
                bolusType = operation.bolusType,
                targetAmount = operation.targetAmount,
                finishAmount = operation.finishAmount,
                infuseStartTime = format(operation.startedAt),
                infuseEndTime = operation.finishedAt?.let(::format)
            )
        )
        aapsLogger.info(
            LTag.WTCONNECT,
            "Remote bolus status operationNo=${operation.operationNo} state=$state wsQueued=$sent"
        )
        if (!sent) {
            statusStore.record("Bolus status could not be sent (${operation.recordNo})", WTConnectLogLevel.WARNING)
        }
    }

    private fun replayExisting(operation: WTConnectOperation) {
        if (operation.state == REJECTED) {
            reject(operation.operationNo, "previous attempt was rejected")
            return
        }
        sendStatus(operation, operation.state)
        if (operation.state == WTInfuseState.COMPLETED || operation.state == WTInfuseState.STOPPED) {
            // Restores the HTTP history enqueue if the process stopped between persisting the
            // terminal operation state and writing the outbox entry.
            syncCompletedBolus(operation)
        }
    }

    private fun syncCompletedBolus(operation: WTConnectOperation) {
        val session = authSessionProvider.currentSession() ?: run {
            aapsLogger.warn(
                LTag.WTCONNECT,
                "Remote bolus HTTP sync skipped without authorization operationNo=${operation.operationNo}"
            )
            return
        }
        httpSync.syncBolusRecord(
            WTBolusRecord(
                recordNo = operation.recordNo,
                familyPhone = session.phoneNumber,
                bolusType = operation.bolusType,
                operationNo = operation.operationNo,
                targetAmount = operation.targetAmount,
                finishAmount = operation.finishAmount,
                infuseStartTime = format(operation.startedAt),
                infuseEndTime = operation.finishedAt?.let(::format)
            )
        )
        aapsLogger.debug(
            LTag.WTCONNECT,
            "Remote bolus HTTP sync queued operationNo=${operation.operationNo} recordNo=${operation.recordNo}"
        )
    }

    private fun reject(operationNo: String?, reason: String) {
        if (operationNo.isNullOrBlank()) return
        val sent = socketManager.send(WTMessageType.COMMAND_REJECT, operationNo, WTCommandReject(operationNo, reason))
        aapsLogger.warn(
            LTag.WTCONNECT,
            "Remote command rejected operationNo=$operationNo reason=$reason wsQueued=$sent"
        )
        statusStore.record("Remote command rejected: $reason", WTConnectLogLevel.WARNING)
    }

    private fun remoteControlEnabled(): Boolean = sp.getBoolean(R.string.key_wtconnect_remote_control_enabled, false)
    private fun format(timestamp: Long): String = timeFormat.format(Instant.ofEpochMilli(timestamp))
    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

    companion object {
        private const val REJECTED = 0
        private val SUPPORTED_PUMPS = setOf(PumpType.APEX, PumpType.EMBECTA, PumpType.LENOMED)
    }
}
