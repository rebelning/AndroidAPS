package app.aaps.core.interfaces.rx.events

/** Fired when the server rejects the currently selected pump identity. */
class EventAapsPumpAuthorizationFailed(val errorInfo: String) : Event()
