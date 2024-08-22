package app.aaps.core.interfaces.pump


interface Apex {
    fun loadHistory(type: Byte): PumpEnactResult // for history browser
    fun loadBolusHistory(): PumpEnactResult // for history browser
    fun setUserOptions(): PumpEnactResult // pump etc settings
}