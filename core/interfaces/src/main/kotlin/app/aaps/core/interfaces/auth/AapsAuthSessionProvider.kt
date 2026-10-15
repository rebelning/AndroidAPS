package app.aaps.core.interfaces.auth

import app.aaps.core.data.pump.defs.PumpType

/** Authentication data shared with integrations without coupling them to aapsauth. */
data class AapsAuthSession(
    val phoneNumber: String,
    val authorizeCode: String,
    val pumpCode: String,
    val pumpType: PumpType,
    val expiresAt: Long
)

interface AapsAuthSessionProvider {

    /** Returns null until login authorization, pump selection and expiry checks all pass. */
    fun currentSession(): AapsAuthSession?

    /** Creates the short-lived appToken used by the WT gateway. */
    fun createAppToken(timestamp: Long = System.currentTimeMillis()): String
}
