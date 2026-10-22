package ovh.battistella.sillon.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import ovh.battistella.sillon.data.jellyfin.SyncPlayApi
import ovh.battistella.sillon.data.plex.PlexDevice
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TogetherModule {
    @Provides
    @Singleton
    fun provideSyncPlayApi(client: OkHttpClient, device: PlexDevice): SyncPlayApi = SyncPlayApi(client, device)
}
