package ovh.battistella.sillon.data.genres

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.plex.PlexMetadata

/**
 * The tracks a genre shuffle leaves out. It keeps everything for now; the 1-star rule of the ratings
 * plugs in here (online: `PlexRepository.withoutOneStar`, offline: `withoutOneStarDownloads`) by
 * changing the binding in [GenreModule], without touching the shuffle itself.
 */
interface GenreShuffleFilter {
    suspend fun online(tracks: List<PlexMetadata>): List<PlexMetadata> = tracks

    suspend fun offline(tracks: List<DownloadEntity>): List<DownloadEntity> = tracks

    object KeepAll : GenreShuffleFilter
}

@Module
@InstallIn(SingletonComponent::class)
object GenreModule {
    @Provides
    fun shuffleFilter(): GenreShuffleFilter = GenreShuffleFilter.KeepAll
}
