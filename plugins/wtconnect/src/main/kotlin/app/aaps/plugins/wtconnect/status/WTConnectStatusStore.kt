package app.aaps.plugins.wtconnect.status

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

enum class WTConnectConnectionState {
    STOPPED,
    WAITING_AUTHORIZATION,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

enum class WTConnectLogLevel {
    INFO,
    WARNING,
    ERROR
}

data class WTConnectUiLog(
    val timestamp: Long,
    val level: WTConnectLogLevel,
    val message: String
)

data class WTConnectStatusSnapshot(
    val connectionState: WTConnectConnectionState,
    val connectionDetail: String?,
    val pendingHttpCount: Int,
    val logs: List<WTConnectUiLog>
)

@Singleton
class WTConnectStatusStore @Inject constructor() {

    private val logs = ArrayDeque<WTConnectUiLog>()
    private var connectionState = WTConnectConnectionState.STOPPED
    private var connectionDetail: String? = null
    private var pendingHttpCount = 0

    @Synchronized
    fun updateConnection(state: WTConnectConnectionState, detail: String? = null) {
        connectionState = state
        connectionDetail = detail
    }

    @Synchronized
    fun updatePendingHttpCount(count: Int) {
        pendingHttpCount = count.coerceAtLeast(0)
    }

    @Synchronized
    fun record(message: String, level: WTConnectLogLevel = WTConnectLogLevel.INFO) {
        logs.addFirst(WTConnectUiLog(System.currentTimeMillis(), level, message))
        while (logs.size > MAX_LOG_ITEMS) logs.removeLast()
    }

    @Synchronized
    fun clearLogs() {
        logs.clear()
    }

    @Synchronized
    fun snapshot(): WTConnectStatusSnapshot =
        WTConnectStatusSnapshot(
            connectionState = connectionState,
            connectionDetail = connectionDetail,
            pendingHttpCount = pendingHttpCount,
            logs = logs.toList()
        )

    companion object {
        private const val MAX_LOG_ITEMS = 200
    }
}
