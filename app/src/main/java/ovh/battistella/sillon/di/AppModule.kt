package ovh.battistella.sillon.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import ovh.battistella.sillon.BuildConfig
import ovh.battistella.sillon.data.jellyfin.JellyfinApi
import ovh.battistella.sillon.data.local.DownloadDao
import ovh.battistella.sillon.data.local.MIGRATION_1_2
import ovh.battistella.sillon.data.local.SillonDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): SillonDatabase =
        Room.databaseBuilder(context, SillonDatabase::class.java, "sillon.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideDownloadDao(db: SillonDatabase): DownloadDao = db.downloadDao()

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

    @Provides
    @Singleton
    fun provideJellyfinApi(client: OkHttpClient, device: PlexDevice): JellyfinApi = JellyfinApi(client, device)
}
