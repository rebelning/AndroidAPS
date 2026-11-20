package app.aaps.core.interfaces.rx.events

import app.aaps.core.data.pump.defs.PumpType

/** Final delivered amount decoded from a pump completion/history packet, never UI progress. */
class EventPumpBolusCompleted(
    val treatmentId: Long,
    val pumpType: PumpType,
    val delivered: Double,
    val timestamp: Long
) : Event()
