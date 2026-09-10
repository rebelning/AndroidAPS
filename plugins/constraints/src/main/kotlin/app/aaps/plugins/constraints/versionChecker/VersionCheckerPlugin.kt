package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.versionChecker.keys.VersionCheckerLongKey
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionCheckerPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val versionCheckerUtils: VersionCheckerUtils,
    private val config: Config,
    private val dateUtil: DateUtil
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.version_checker),
    ownPreferences = listOf(VersionCheckerLongKey::class.java),
    aapsLogger, rh, preferences
), PluginConstraints {

    enum class GracePeriod(val warning: Long, val old: Long, val veryOld: Long) {
        RELEASE(360, 720, 720),
        RC(2, 7, 14)
        // RELEASE(30, 60, 90),
        // RC(2, 7, 14)
    }

    private val gracePeriod: GracePeriod
        get() = if ((config.VERSION_NAME.contains("RC", ignoreCase = true))) {
            GracePeriod.RC
        } else {
            GracePeriod.RELEASE
        }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        versionCheckerUtils.triggerCheckVersion()
        if (lastCheckOlderThan(gracePeriod.veryOld.daysToMillis()))
            value.set(false, rh.gs(R.string.very_old_version), this)
        val endDate = preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME)
        if (endDate != 0L && dateUtil.now() > endDate)
            value.set(false, rh.gs(R.string.application_expired), this)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        versionCheckerUtils.triggerCheckVersion()
        val endDate = preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME)
        return if (lastCheckOlderThan(gracePeriod.old.daysToMillis()))
            maxIob.set(0.0, rh.gs(R.string.old_version), this)
        else if (endDate != 0L && dateUtil.now() > endDate)
            maxIob.set(0.0, rh.gs(R.string.application_expired), this)
        else
            maxIob
    }

    private fun lastCheckOlderThan(gracePeriod: Long): Boolean =
        dateUtil.now() > preferences.get(VersionCheckerLongKey.LastSuccessfulVersionCheck) + gracePeriod

    private fun Long.daysToMillis() = TimeUnit.DAYS.toMillis(this)
}
