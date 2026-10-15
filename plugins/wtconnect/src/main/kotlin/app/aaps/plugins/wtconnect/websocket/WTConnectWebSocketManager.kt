package app.aaps.plugins.wtconnect.websocket

import app.aaps.core.interfaces.auth.AapsAuthSession
import app.aaps.core.interfaces.auth.AapsAuthSessionProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.wtconnect.api.WTConnectApi
import app.aaps.plugins.wtconnect.api.WTConnectNetworkConfig
import app.aaps.plugins.wtconnect.api.WTEnvelope
import app.aaps.plugins.wtconnect.api.WTMessageType
import app.aaps.plugins.wtconnect.api.WTRequest
import app.aaps.plugins.wtconnect.api.WTTicketParams
import app.aaps.plugins.wtconnect.status.WTConnectConnectionState
import app.aaps.plugins.wtconnect.status.WTConnectLogLevel
import app.aaps.plugins.wtconnect.status.WTConnectStatusStore
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class WTConnectWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val api: WTConnectApi,
    private val gson: Gson,
    private val authSessionProvider: AapsAuthSessionProvider,
    private val statusStore: WTConnectStatusStore,
    private val aapsLogger: AAPSLogger
) {
    @Volatile private var socket: WebSocket? = null
    @Volatile private var pendingSocket: WebSocket? = null
    @Volatile private var connectedSession: AapsAuthSession? = null
    @Volatile private var started = false
    @Volatile private var connecting = false
    @Volatile private var reconnectAttempt = 0
    @Volatile private var connectionGeneration = 0L
    private var worker: ScheduledExecutorService? = null
    private var commandHandler: ((String) -> Unit)? = null

    val connected: Boolean get() = socket != null && connectedSession != null

    fun setCommandHandler(handler: (String) -> Unit) {
        commandHandler = handler
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        connectionGeneration += 1
        aapsLogger.info(LTag.WTCONNECT, "WebSocket manager starting")
        statusStore.updateConnection(WTConnectConnectionState.CONNECTING)
        statusStore.record("WebSocket service started")
        worker = Executors.newSingleThreadScheduledExecutor().also { executor ->
            executor.execute(::connect)
            executor.scheduleWithFixedDelay(::heartbeat, 120, 120, TimeUnit.SECONDS)
        }
    }

    @Synchronized
    fun stop() {
        aapsLogger.info(LTag.WTCONNECT, "WebSocket manager stopping")
        started = false
        connectionGeneration += 1
        connecting = false
        connectedSession = null
        pendingSocket?.cancel()
        pendingSocket = null
        socket?.close(1000, "WTConnect disabled")
        socket = null
        worker?.shutdownNow()
        worker = null
        aapsLogger.info(LTag.WTCONNECT, "WebSocket manager stopped")
        statusStore.updateConnection(WTConnectConnectionState.STOPPED)
        statusStore.record("WebSocket service stopped")
    }

    fun reconnectForIdentityChange() {
        aapsLogger.info(LTag.WTCONNECT, "WebSocket reconnect requested for identity change")
        statusStore.record("Pump identity changed; reconnecting WebSocket")
        reconnect("identity changed")
    }

    fun reconnectManually() {
        aapsLogger.info(LTag.WTCONNECT, "Manual WebSocket reconnect requested")
        statusStore.record("Manual WebSocket reconnect requested")
        reconnect("manual reconnect")
    }

    private fun reconnect(reason: String) {
        statusStore.updateConnection(WTConnectConnectionState.RECONNECTING, reason)
        worker?.execute {
            connectionGeneration += 1
            connecting = false
            connectedSession = null
            pendingSocket?.cancel()
            pendingSocket = null
            socket?.close(1000, "WTConnect $reason")
            socket = null
            connect()
        }
    }

    fun send(type: String, bizNo: String?, data: Any): Boolean {
        val current = socket ?: run {
            aapsLogger.debug(LTag.WTCONNECT, "WebSocket send skipped while disconnected type=$type")
            return false
        }
        val message = WTEnvelope(
            msgId = UUID.randomUUID().toString().replace("-", ""),
            type = type,
            bizNo = bizNo,
            timestamp = System.currentTimeMillis(),
            data = data
        )
        val accepted = current.send(gson.toJson(message))
        if (accepted) {
            aapsLogger.debug(LTag.WTCONNECT, "WebSocket message queued type=$type hasBizNo=${!bizNo.isNullOrBlank()}")
        } else {
            aapsLogger.warn(LTag.WTCONNECT, "WebSocket message rejected by client queue type=$type")
        }
        return accepted
    }

    fun sendGlucose(data: Any): Boolean = send(WTMessageType.GLUCOSE_DATA, null, data)

    private fun connect() {
        if (!started || connecting || socket != null) return
        val generation = connectionGeneration
        val session = authSessionProvider.currentSession()
        if (session == null) {
            statusStore.updateConnection(WTConnectConnectionState.WAITING_AUTHORIZATION)
            scheduleReconnect("waiting for authorization or pump selection")
            return
        }
        connecting = true
        statusStore.updateConnection(WTConnectConnectionState.CONNECTING, "Requesting connection ticket")
        aapsLogger.debug(LTag.WTCONNECT, "Requesting PHONE WebSocket ticket pumpType=${session.pumpType}")
        try {
            val ticketResponse = api.phoneTicket(
                appToken = authSessionProvider.createAppToken(),
                body = WTRequest(WTTicketParams(session.phoneNumber, session.authorizeCode, session.pumpCode))
            ).execute()
            val ticket = ticketResponse.body()?.result?.ticket
            if (!ticketResponse.isSuccessful || ticketResponse.body()?.returnCode != "0" || ticket.isNullOrBlank()) {
                connecting = false
                statusStore.record("WebSocket ticket request rejected", WTConnectLogLevel.WARNING)
                scheduleReconnect("ticket request rejected (${ticketResponse.body()?.errorNo ?: ticketResponse.code()})")
                return
            }
            aapsLogger.debug(LTag.WTCONNECT, "PHONE WebSocket ticket received pumpType=${session.pumpType}")
            val encodedTicket = URLEncoder.encode(ticket, Charsets.UTF_8.name())
            val request = Request.Builder().url("${WTConnectNetworkConfig.WS_URL}?ticket=$encodedTicket").build()
            val newSocket = okHttpClient.newWebSocket(request, listener(session, generation))
            if (!started || generation != connectionGeneration) {
                newSocket.cancel()
            } else {
                pendingSocket = newSocket
            }
        } catch (error: Exception) {
            if (generation == connectionGeneration) {
                connecting = false
                statusStore.record(
                    "WebSocket ticket request failed: ${error.message ?: error.javaClass.simpleName}",
                    WTConnectLogLevel.WARNING
                )
                scheduleReconnect("ticket request failed: ${error.message}")
            }
        }
    }

    private fun listener(session: AapsAuthSession, generation: Long) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!started || generation != connectionGeneration) {
                webSocket.close(1000, "WTConnect session changed")
                if (pendingSocket === webSocket) pendingSocket = null
                return
            }
            if (authSessionProvider.currentSession() != session) {
                connectionGeneration += 1
                connecting = false
                pendingSocket = null
                webSocket.close(1000, "WTConnect session changed")
                scheduleReconnect("session changed during WebSocket handshake")
                return
            }
            pendingSocket = null
            socket = webSocket
            connectedSession = session
            connecting = false
            reconnectAttempt = 0
            aapsLogger.info(LTag.WTCONNECT, "WebSocket connected for pumpType=${session.pumpType}")
            statusStore.updateConnection(WTConnectConnectionState.CONNECTED, "Pump type: ${session.pumpType}")
            statusStore.record("WebSocket connected (${session.pumpType})")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.contains("\"type\":\"pong\"", ignoreCase = true)) return
            aapsLogger.debug(LTag.WTCONNECT, "WebSocket command message received")
            worker?.execute { commandHandler?.invoke(text) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleClosed(webSocket, generation, "closed code=$code")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleClosed(webSocket, generation, "failure=${t.message}")
        }
    }

    private fun handleClosed(closedSocket: WebSocket, generation: Long, reason: String) {
        if (generation != connectionGeneration) return
        if (socket === closedSocket || pendingSocket === closedSocket || (connecting && socket == null)) {
            connectionGeneration += 1
            socket = null
            pendingSocket = null
            connectedSession = null
            connecting = false
            statusStore.record("WebSocket disconnected: $reason", WTConnectLogLevel.WARNING)
            scheduleReconnect(reason)
        }
    }

    private fun heartbeat() {
        if (!started) return
        val current = authSessionProvider.currentSession()
        if (current == null || current != connectedSession) {
            reconnectForIdentityChange()
            return
        }
        if (socket?.send("{\"type\":\"ping\"}") != true) {
            statusStore.record("WebSocket heartbeat failed", WTConnectLogLevel.WARNING)
            connectionGeneration += 1
            connecting = false
            pendingSocket?.cancel()
            pendingSocket = null
            socket?.cancel()
            socket = null
            connectedSession = null
            connect()
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (!started) return
        reconnectAttempt += 1
        val delay = min(5L * (1L shl min(reconnectAttempt - 1, 5)), 120L)
        val generation = connectionGeneration
        aapsLogger.warn(LTag.WTCONNECT, "WebSocket reconnect in ${delay}s: $reason")
        if (statusStore.snapshot().connectionState != WTConnectConnectionState.WAITING_AUTHORIZATION) {
            statusStore.updateConnection(WTConnectConnectionState.RECONNECTING, "Retry in ${delay}s: $reason")
        }
        worker?.schedule({ if (generation == connectionGeneration) connect() }, delay, TimeUnit.SECONDS)
    }
}
