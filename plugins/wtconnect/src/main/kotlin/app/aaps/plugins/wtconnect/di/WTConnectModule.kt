package app.aaps.plugins.wtconnect.di

import android.content.Context
import app.aaps.plugins.wtconnect.api.WTConnectApi
import app.aaps.plugins.wtconnect.api.WTConnectNetworkConfig
import app.aaps.plugins.wtconnect.database.WTConnectDao
import app.aaps.plugins.wtconnect.database.WTConnectDatabase
import app.aaps.plugins.wtconnect.ui.WTConnectFragment
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module(includes = [WTConnectModule.Injectors::class])
object WTConnectModule {

    @Module
    abstract class Injectors {

        @ContributesAndroidInjector
        abstract fun contributesWTConnectFragment(): WTConnectFragment
    }

    @Provides
    @Singleton
    fun database(context: Context): WTConnectDatabase = WTConnectDatabase.build(context)

    @Provides
    fun dao(database: WTConnectDatabase): WTConnectDao = database.dao()

    @Provides
    @Singleton
    fun gson(): Gson = Gson()

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun api(okHttpClient: OkHttpClient, gson: Gson): WTConnectApi = Retrofit.Builder()
        .baseUrl(WTConnectNetworkConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(WTConnectApi::class.java)
}
