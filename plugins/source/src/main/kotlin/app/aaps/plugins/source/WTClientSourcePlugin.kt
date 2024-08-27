package app.aaps.plugins.source

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.WTClientSource
import app.aaps.database.entities.GlucoseValue
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WTClientSourcePlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_nsclient_bg)
        .pluginName(R.string.wt_client_bg)
        .shortName(R.string.wt_client_bg_short)
        .description(R.string.description_source_wt_client)
        .alwaysEnabled(config.NSCLIENT)
        .setDefault(config.NSCLIENT),
    aapsLogger, rh, injector
), BgSource, WTClientSource {

    private var lastBGTimeStamp: Long = 0
    private var isAdvancedFilteringEnabled = false

    override fun advancedFilteringSupported(): Boolean = isAdvancedFilteringEnabled

    override fun detectSource(glucoseValue: GlucoseValue) {
        if (glucoseValue.timestamp > lastBGTimeStamp) {
            isAdvancedFilteringEnabled = arrayOf(
                GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN,
                GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE,
                GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE,
                GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
                GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP,
                GlucoseValue.SourceSensor.WT_NET
            ).any { it == glucoseValue.sourceSensor }
            lastBGTimeStamp = glucoseValue.timestamp
        }
    }
}