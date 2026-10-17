package ovh.battistella.sillon.di

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import ovh.battistella.sillon.BuildConfig
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun providePlexDevice(): PlexDevice = PlexDevice(
        product = "Sillon",
        version = BuildConfig.VERSION_NAME,
        platformVersion = Build.VERSION.RELEASE.orEmpty(),
        deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .ifBlank { "Android" },
    )

    @Provides
    @Singleton
    fun providePlexApi(client: OkHttpClient, device: PlexDevice): PlexApi = PlexApi(client, device)
}
