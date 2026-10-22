package ovh.battistella.sillon.playback.mystery

import androidx.media3.common.MediaItem
import ovh.battistella.sillon.data.settings.SavedMystery
import ovh.battistella.sillon.data.settings.SavedMysteryTrack
import ovh.battistella.sillon.playback.MediaItems
import ovh.battistella.sillon.playback.PlayableTrack

fun Mystery.toSaved(): SavedMystery = SavedMystery(
    albumKey = albumKey,
    tracks = tracks.map { SavedMysteryTrack(it.track.ratingKey, it.side, it.number) },
    offline = offline,
    revealed = revealed,
    left = left.toList(),
)

object MysteryResume {
    // The saved queue only keeps track ids, so a resumed queue must be masked again before anyone sees it.
    fun restore(
        saved: SavedMystery?,
        tracks: List<PlayableTrack>,
        labels: SleeveLabels,
        session: MysterySession,
    ): List<MediaItem> {
        val mystery = saved?.let { rebuild(it, tracks) }
        if (mystery == null) {
            session.clear()
            return tracks.map(MediaItems::from)
        }
        session.start(mystery)
        if (mystery.revealed) return tracks.map(MediaItems::from)
        val hidden = mystery.tracks.associateBy { it.track.ratingKey }
        return tracks.map { track -> hidden[track.ratingKey]?.let { MysteryMask.mask(it, labels) } ?: MediaItems.from(track) }
    }

    private fun rebuild(saved: SavedMystery, tracks: List<PlayableTrack>): Mystery? {
        val byKey = tracks.associateBy { it.ratingKey }
        val restored = saved.tracks.mapNotNull { t -> byKey[t.key]?.let { MysteryTrack(it, t.side, t.number) } }
        if (restored.isEmpty()) return null
        return Mystery(
            albumKey = saved.albumKey,
            tracks = restored,
            offline = saved.offline,
            revealed = saved.revealed,
            revealSeen = saved.revealed,
            left = saved.left.toSet(),
        )
    }
}
