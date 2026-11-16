package ovh.battistella.sillon.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ovh.battistella.sillon.data.network.AndroidNetworkMonitor
import ovh.battistella.sillon.data.network.NetworkMonitor
import ovh.battistella.sillon.data.storage.AndroidStorage
import ovh.battistella.sillon.data.storage.AppStorage
import ovh.battistella.sillon.playback.cache.ListeningCache
import ovh.battistella.sillon.playback.cache.TrackCache

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds
    abstract fun storage(storage: AndroidStorage): AppStorage

    @Binds
    abstract fun network(monitor: AndroidNetworkMonitor): NetworkMonitor

    @Binds
    abstract fun trackCache(cache: ListeningCache): TrackCache
}
