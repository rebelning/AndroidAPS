package app.aaps.core.interfaces.rx.events
// pass string to startup wizard
abstract class EventLogStatus : Event() {
    abstract fun setCmdID(id: Int) : Int
}