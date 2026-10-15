package app.aaps.plugins.wtconnect.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class WTRequest<T>(@SerializedName("params") val params: T)

data class WTResponse(
    val returnCode: String? = null,
    val errorNo: String? = null,
    val errorInfo: String? = null,
    val result: JsonElement? = null
) {
    val successful: Boolean get() = returnCode == "0"
}

data class WTTicketResponse(
    val returnCode: String? = null,
    val errorNo: String? = null,
    val errorInfo: String? = null,
    val result: WTTicketResult? = null
)

data class WTTicketResult(val ticket: String, val expireSeconds: Int = 60)

data class WTTicketParams(
    val phoneNumber: String,
    val code: String,
    val pumpCode: String
)

data class WTBolusRecord(
    val recordNo: String,
    val familyPhone: String,
    val bolusType: Int,
    val operationNo: String? = null,
    val targetAmount: Double? = null,
    val finishAmount: Double? = null,
    val infuseStartTime: String? = null,
    val infuseEndTime: String? = null
)

data class WTPumpStatus(
    val familyPhone: String,
    val deviceModel: String? = null,
    val deviceNo: String? = null,
    val deviceRunningStatus: Int? = null,
    val battery: Int? = null,
    val residueAmount: Double? = null,
    val dayTotalAmount: Double? = null,
    val bolusMax: Double? = null,
    val basalMax: Double? = null,
    val currentBasal: Double? = null,
    val tempBasal: Double? = null,
    val tempBasalType: Int? = null,
    val syncTime: String? = null
)

data class WTGlucoseData(
    val familyPhone: String? = null,
    val termInfo: WTTermInfo,
    val termData: List<WTGlucosePoint>
)

data class WTTermInfo(
    val probeNo: String,
    val termNo: String? = null,
    val termType: Int? = TERM_TYPE_APPS
) {
    companion object { const val TERM_TYPE_APPS = 6 }
}

data class WTGlucosePoint(
    val value: String,
    val valueIndex: String,
    val recordTime: String? = null,
    val arrowState: String? = null
)

data class WTBasalProfile(
    val familyPhone: String,
    val deviceRecordTime: String? = null,
    val rateList: List<WTBasalRate>
)

data class WTBasalRate(val startMinute: Int, val rate: Double)

data class WTBasalHistory(
    val familyPhone: String,
    val recordDate: String,
    val deviceRecordTime: String,
    val segmentList: List<WTBasalSegment>
)

data class WTBasalSegment(val startMinute: Int, val amount: Double)

data class WTAlarmSync(val familyPhone: String, val alarmList: List<WTAlarm>)

data class WTAlarm(val alarmType: Int, val alarmTime: String, val alarmContent: String? = null)

data class WTTempBasalSync(val familyPhone: String, val records: List<WTTempBasalRecord>)

data class WTTempBasalRecord(
    val recordNo: String,
    val tempBasalType: Int,
    val tempBasalValue: Double,
    val startTime: String,
    val endTime: String? = null,
    val totalTime: Int? = null,
    val runTime: Int? = null,
    val status: Int? = null
)

data class WTEnvelope<T>(
    val msgId: String,
    val type: String,
    val bizNo: String? = null,
    val timestamp: Long,
    val data: T
)

data class WTBolusInfuseCommand(
    val operationNo: String,
    val bolusType: Int,
    val targetAmount: Double,
    val infuseStartTime: String? = null
)

data class WTBolusStopCommand(
    val operationNo: String,
    val targetOperationNo: String,
    val recordNo: String? = null
)

data class WTInfuseStatus(
    val operationNo: String,
    val recordNo: String,
    val infuseStatus: Int,
    val bolusType: Int,
    val targetAmount: Double? = null,
    val finishAmount: Double? = null,
    val infuseStartTime: String? = null,
    val infuseEndTime: String? = null
)

data class WTBolusProgress(
    val operationNo: String,
    val recordNo: String,
    val finishAmount: Double
)

data class WTCommandReject(val operationNo: String, val reason: String? = null)

object WTMessageType {
    const val BOLUS_INFUSE = "BOLUS_INFUSE"
    const val BOLUS_STOP = "BOLUS_STOP"
    const val PUMP_PAUSE = "PUMP_PAUSE"
    const val PUMP_RESUME = "PUMP_RESUME"
    const val LOOP_CLOSE = "LOOP_CLOSE"
    const val LOOP_START = "LOOP_START"
    const val INFUSE_STATUS = "INFUSE_STATUS"
    const val BOLUS_PROGRESS = "BOLUS_PROGRESS"
    const val GLUCOSE_DATA = "GLUCOSE_DATA"
    const val COMMAND_REJECT = "COMMAND_REJECT"
}

object WTInfuseState {
    const val SENT = 1
    const val STARTED = 2
    const val COMPLETED = 3
    const val STOPPED = 4
}
