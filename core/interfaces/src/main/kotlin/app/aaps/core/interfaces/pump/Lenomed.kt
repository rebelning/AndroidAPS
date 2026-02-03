package app.aaps.core.interfaces.pump

interface Lenomed {

    fun loadHistory(type:Byte): PumpEnactResult // for history browser
    fun loadBolusHistory(): PumpEnactResult // for history browser
    fun setUserOptions(): PumpEnactResult // pump etc settings
}