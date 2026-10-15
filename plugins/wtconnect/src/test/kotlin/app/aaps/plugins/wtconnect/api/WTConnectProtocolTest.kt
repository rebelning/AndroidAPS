package app.aaps.plugins.wtconnect.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WTConnectProtocolTest {

    private val gson = Gson()

    @Test
    fun exposesExactlyTheSevenDocumentedHttpEndpoints() {
        val endpoints = setOf(
            WTConnectEndpoints.BOLUS_RECORD,
            WTConnectEndpoints.PUMP_STATUS,
            WTConnectEndpoints.GLUCOSE_DATA,
            WTConnectEndpoints.BASAL_PROFILE,
            WTConnectEndpoints.BASAL_HISTORY,
            WTConnectEndpoints.ALARM,
            WTConnectEndpoints.TEMP_BASAL
        )

        assertEquals(7, endpoints.size)
        assertTrue(endpoints.all { it.startsWith("v1/family/aps/sync") })
    }

    @Test
    fun websocketGlucoseOmitsPhoneNumberAndKeepsNumericIndex() {
        val json = gson.toJson(
            WTGlucoseData(
                termInfo = WTTermInfo(probeNo = "AAPS-test", termNo = "2508220001"),
                termData = listOf(WTGlucosePoint(value = "6.8", valueIndex = "1791643479895"))
            )
        )

        assertFalse(json.contains("familyPhone"))
        assertTrue(json.contains("\"valueIndex\":\"1791643479895\""))
        assertTrue(json.contains("\"termType\":6"))
    }

    @Test
    fun remoteBolusEnvelopePreservesOperationNumber() {
        val operationNo = "OP20260926103000123"
        val json = gson.toJson(
            WTEnvelope(
                msgId = "message-id",
                type = WTMessageType.INFUSE_STATUS,
                bizNo = operationNo,
                timestamp = 1L,
                data = WTInfuseStatus(
                    operationNo = operationNo,
                    recordNo = "R202609261030",
                    infuseStatus = WTInfuseState.STARTED,
                    bolusType = 1,
                    targetAmount = 5.0,
                    finishAmount = 1.2
                )
            )
        )

        assertTrue(json.contains("\"bizNo\":\"$operationNo\""))
        assertTrue(json.contains("\"operationNo\":\"$operationNo\""))
        assertTrue(json.contains("\"infuseStatus\":2"))
    }
}
