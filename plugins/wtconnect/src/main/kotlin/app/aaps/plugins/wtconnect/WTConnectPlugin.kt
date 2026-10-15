package app.aaps.plugins.wtconnect

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.plugins.wtconnect.command.WTConnectCommandDispatcher
import app.aaps.plugins.wtconnect.status.WTConnectLogLevel
import app.aaps.plugins.wtconnect.status.WTConnectStatusStore
import app.aaps.plugins.wtconnect.sync.WTConnectHttpSync
import app.aaps.plugins.wtconnect.sync.WTConnectTelemetryCollector
import app.aaps.plugins.wtconnect.ui.WTConnectFragment
import app.aaps.plugins.wtconnect.websocket.WTConnectWebSocketManager
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WTConnectPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val socketManager: WTConnectWebSocketManager,
    private val commandDispatcher: WTConnectCommandDispatcher,
    private val httpSync: WTConnectHttpSync,
    private val telemetry: WTConnectTelemetryCollector,
    private val statusStore: WTConnectStatusStore
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_nightscout_syncs)
        .pluginName(R.string.wtconnect_name)
        .shortName(R.string.wtconnect_short_name)
        .fragmentClass(WTConnectFragment::class.java.name)
        .preferencesId(R.xml.pref_wtconnect)
        .description(R.string.wtconnect_description)
        .enableByDefault(false)
        .visibleByDefault(true),
    aapsLogger,
    rh
) {
    private val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        aapsLogger.info(LTag.WTCONNECT, "WTConnect plugin starting")
        httpSync.start()
        socketManager.setCommandHandler(commandDispatcher::handle)
        socketManager.start()

        disposable += rxBus.toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ telemetry.syncLatestGlucose() }, ::logError)
        disposable += rxBus.toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                telemetry.syncPumpStatus()
                telemetry.syncBasalHistory()
            }, ::logError)
        disposable += rxBus.toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                telemetry.syncLatestBolus()
                telemetry.syncLatestTempBasal()
                telemetry.syncBasalHistory()
            }, ::logError)
        disposable += rxBus.toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ telemetry.syncLatestTempBasal() }, ::logError)
        disposable += rxBus.toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ telemetry.syncBasalProfile() }, ::logError)
        disposable += rxBus.toObservable(EventProfileStoreChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ telemetry.syncBasalProfile() }, ::logError)
        disposable += rxBus.toObservable(EventLocalProfileChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ telemetry.syncBasalProfile() }, ::logError)
        disposable += rxBus.toObservable(EventNewNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ telemetry.syncAlarm(it.notification) }, ::logError)
        disposable += rxBus.toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe(commandDispatcher::onBolusProgress, ::logError)
        disposable += rxBus.toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                if (event.changedKey in PUMP_IDENTITY_KEYS) {
                    aapsLogger.info(LTag.WTCONNECT, "Pump identity preference changed; reconnecting WebSocket")
                    socketManager.reconnectForIdentityChange()
                }
            }, ::logError)
        disposable += rxBus.toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ stopServices() }, ::logError)
        disposable += Observable.interval(
            millisUntilNextHour(),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.MILLISECONDS
        )
            .observeOn(aapsSchedulers.io)
            .subscribe({ telemetry.syncBasalHistory() }, ::logError)

        telemetry.syncPumpStatus()
        telemetry.syncBasalProfile()
        telemetry.syncBasalHistory()
        telemetry.syncLatestGlucose()
        aapsLogger.info(LTag.WTCONNECT, "WTConnect plugin started")
    }

    override fun onStop() {
        disposable.clear()
        stopServices()
        super.onStop()
    }

    private fun stopServices() {
        aapsLogger.info(LTag.WTCONNECT, "WTConnect services stopping")
        socketManager.stop()
        httpSync.stop()
        aapsLogger.info(LTag.WTCONNECT, "WTConnect services stopped")
    }

    private fun logError(error: Throwable) {
        aapsLogger.error(LTag.WTCONNECT, "WTConnect event handling failed", error)
        statusStore.record("Event handling failed: ${error.message ?: error.javaClass.simpleName}", WTConnectLogLevel.ERROR)
    }

    private fun millisUntilNextHour(): Long {
        val now = ZonedDateTime.now()
        val nextHour = now.plusHours(1).withMinute(0).withSecond(1).withNano(0)
        return Duration.between(now, nextHour).toMillis().coerceAtLeast(1_000L)
    }

    companion object {
        private val PUMP_IDENTITY_KEYS = setOf(
            "key_pump_apexname",
            "key_pump_embectaname",
            "key_pump_lenomedname"
        )
    }
}
