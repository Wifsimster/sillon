package ovh.battistella.sillon.data.genres

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ovh.battistella.sillon.data.local.DownloadEntity
import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.rating.withoutOneStarDownloads
import ovh.battistella.sillon.data.repository.PlexRepository

/** The tracks a genre shuffle leaves out. Both methods keep everything unless overridden. */
interface GenreShuffleFilter {
    suspend fun online(tracks: List<PlexMetadata>): List<PlexMetadata> = tracks

    suspend fun offline(tracks: List<DownloadEntity>): List<DownloadEntity> = tracks

    /** The 1-star rule of the ratings: a track rated 1 star never comes into a genre shuffle, online or offline. */
    class WithoutOneStar(private val repository: PlexRepository) : GenreShuffleFilter {
        override suspend fun online(tracks: List<PlexMetadata>): List<PlexMetadata> = repository.withoutOneStar(tracks)

        override suspend fun offline(tracks: List<DownloadEntity>): List<DownloadEntity> =
            repository.ratingBook().withoutOneStarDownloads(tracks)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GenreModule {
    @Provides
    fun shuffleFilter(repository: PlexRepository): GenreShuffleFilter = GenreShuffleFilter.WithoutOneStar(repository)
}
