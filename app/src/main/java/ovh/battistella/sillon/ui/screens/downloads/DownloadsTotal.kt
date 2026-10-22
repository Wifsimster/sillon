package ovh.battistella.sillon.ui.screens.downloads

import ovh.battistella.sillon.data.local.DownloadEntity

/** What the downloads hold, as the settings entry sums it up: albums as the downloads screen lists them, and bytes on the phone. */
data class DownloadsTotal(val albums: Int, val bytes: Long) {
    companion object {
        val NONE = DownloadsTotal(albums = 0, bytes = 0)

        fun of(rows: List<DownloadEntity>) = DownloadsTotal(
            albums = rows.map { it.albumKey ?: NO_ALBUM }.distinct().size,
            bytes = rows.sumOf { it.sizeBytes },
        )
    }
}
