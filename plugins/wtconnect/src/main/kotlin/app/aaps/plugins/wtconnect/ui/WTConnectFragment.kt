package app.aaps.plugins.wtconnect.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.plugins.wtconnect.R
import app.aaps.plugins.wtconnect.databinding.WtconnectFragmentBinding
import app.aaps.plugins.wtconnect.databinding.WtconnectLogItemBinding
import app.aaps.plugins.wtconnect.status.WTConnectConnectionState
import app.aaps.plugins.wtconnect.status.WTConnectLogLevel
import app.aaps.plugins.wtconnect.status.WTConnectStatusStore
import app.aaps.plugins.wtconnect.status.WTConnectUiLog
import app.aaps.plugins.wtconnect.websocket.WTConnectWebSocketManager
import dagger.android.support.DaggerFragment
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

class WTConnectFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var statusStore: WTConnectStatusStore
    @Inject lateinit var socketManager: WTConnectWebSocketManager

    override var plugin: PluginBase? = null

    private var _binding: WtconnectFragmentBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = LogAdapter()
    private val refreshTask = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        WtconnectFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = adapter
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(refreshTask)
        binding.logList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menu.add(Menu.FIRST, MENU_RECONNECT, 0, R.string.wtconnect_reconnect)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, MENU_CLEAR_LOG, 1, R.string.wtconnect_clear_log)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
        when (menuItem.itemId) {
            MENU_RECONNECT -> {
                socketManager.reconnectManually()
                true
            }

            MENU_CLEAR_LOG -> {
                statusStore.clearLogs()
                updateStatus()
                true
            }

            else -> false
        }

    private fun updateStatus() {
        val currentBinding = _binding ?: return
        val snapshot = statusStore.snapshot()
        currentBinding.connectionStatus.text = getString(
            when (snapshot.connectionState) {
                WTConnectConnectionState.STOPPED               -> R.string.wtconnect_status_stopped
                WTConnectConnectionState.WAITING_AUTHORIZATION -> R.string.wtconnect_status_waiting_authorization
                WTConnectConnectionState.CONNECTING            -> R.string.wtconnect_status_connecting
                WTConnectConnectionState.CONNECTED             -> R.string.wtconnect_status_connected
                WTConnectConnectionState.RECONNECTING          -> R.string.wtconnect_status_reconnecting
            }
        )
        currentBinding.connectionDetail.text = snapshot.connectionDetail.orEmpty()
        currentBinding.connectionDetail.visibility =
            if (snapshot.connectionDetail.isNullOrBlank()) View.GONE else View.VISIBLE
        currentBinding.pendingCount.text = snapshot.pendingHttpCount.toString()
        adapter.replace(snapshot.logs)
        currentBinding.emptyLog.visibility = if (snapshot.logs.isEmpty()) View.VISIBLE else View.GONE
    }

    private class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

        private var items: List<WTConnectUiLog> = emptyList()
        private val timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

        fun replace(newItems: List<WTConnectUiLog>) {
            if (items == newItems) return
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(WtconnectLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.logTime.text = timeFormat.format(Date(item.timestamp))
            holder.binding.logMessage.text = when (item.level) {
                WTConnectLogLevel.INFO    -> item.message
                WTConnectLogLevel.WARNING -> "⚠ ${item.message}"
                WTConnectLogLevel.ERROR   -> "✕ ${item.message}"
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(val binding: WtconnectLogItemBinding) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        private const val MENU_RECONNECT = 6101
        private const val MENU_CLEAR_LOG = 6102
        private const val REFRESH_INTERVAL_MS = 1_000L
    }
}
