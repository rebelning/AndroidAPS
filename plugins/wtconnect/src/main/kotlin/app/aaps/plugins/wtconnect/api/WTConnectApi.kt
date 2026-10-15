package app.aaps.plugins.wtconnect.api

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface WTConnectApi {

    @POST("ws/ticket/phone")
    fun phoneTicket(
        @Header("appToken") appToken: String,
        @Body body: WTRequest<WTTicketParams>
    ): Call<WTTicketResponse>

    @POST
    fun sync(
        @Url endpoint: String,
        @Header("appToken") appToken: String,
        @Body body: JsonObject
    ): Call<WTResponse>
}

object WTConnectEndpoints {
    const val BOLUS_RECORD = "v1/family/aps/syncBolusRecord"
    const val PUMP_STATUS = "v1/family/aps/syncPumpStatus"
    const val GLUCOSE_DATA = "v1/family/aps/syncGlucoseData"
    const val BASAL_PROFILE = "v1/family/aps/syncBasalProfile"
    const val BASAL_HISTORY = "v1/family/aps/syncBasalHistory"
    const val ALARM = "v1/family/aps/syncAlarm"
    const val TEMP_BASAL = "v1/family/aps/syncTempBasal"
}

object WTConnectNetworkConfig {
    const val BASE_URL = "https://test.yidaosubeng.com/wt_gateway/"
    const val WS_URL = "wss://test.yidaosubeng.com/wt_glucose_data/ws"
}
