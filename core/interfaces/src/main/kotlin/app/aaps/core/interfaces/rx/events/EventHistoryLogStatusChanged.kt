package app.aaps.core.interfaces.rx.events


class EventHistoryLogStatusChanged : EventLogStatus {

    enum class Status {
        SEND_CMD,
        HANDLE_CMD,
        HANDLE_CMD_END,

    }

    var status: Status = Status.SEND_CMD
    var cmdId = 0


    constructor(status: Status,id: Int) {
        this.status = status
        cmdId = id

    }



    // status for startup wizard
    override fun setCmdID(id: Int): Int {
        cmdId=id
       return cmdId
    }

  fun  getCmdID(): Int {
        return cmdId
    }
}
