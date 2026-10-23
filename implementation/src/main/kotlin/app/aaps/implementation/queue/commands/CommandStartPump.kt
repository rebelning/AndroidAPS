package app.aaps.implementation.queue.commands

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Insight
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Provider

class CommandStartPump(
    injector: HasAndroidInjector,
    override val callback: Callback?,
) : Command {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    init {
        injector.androidInjector().inject(this)
    }

    override val commandType: Command.CommandType = Command.CommandType.START_PUMP

    override fun execute() {
        val pump = activePlugin.activePump
        val result = when {
            pump is Insight -> pump.startPump()
            pump.model() == PumpType.EMBECTA -> pump.setPauseResumePump(1)
            pump.model() == PumpType.APEX -> pump.setPauseResumePump(0)
            else -> pumpEnactResultProvider.get()
                .success(false)
                .enacted(false)
                .comment("Pump start is not supported by ${pump.model().description}")
        }
        callback?.result(result)?.run()
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.start_pump)

    override fun log(): String = "START PUMP"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}
