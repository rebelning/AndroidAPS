package app.aaps.implementation.instantiator

import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.PumpEnactResult
import dagger.Reusable
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class InstantiatorImpl @Inject constructor(
    private val profileStoreProvider: Provider<ProfileStore>,
    private val apsResultProvider: Provider<APSResult>,
    private val autosensDataProvider: Provider<AutosensData>,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) : Instantiator {

    override fun provideProfileStore(jsonObject: JSONObject): ProfileStore = profileStoreProvider.get().with(jsonObject)
    override fun provideAPSResultObject(rt: RT): APSResult = apsResultProvider.get().with(rt)
    override fun provideAutosensDataObject(): AutosensData = autosensDataProvider.get()
    override fun providePumpEnactResult(): PumpEnactResult = pumpEnactResultProvider.get()
}
