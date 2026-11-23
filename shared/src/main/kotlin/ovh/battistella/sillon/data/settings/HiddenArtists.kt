package ovh.battistella.sillon.data.settings

import ovh.battistella.sillon.data.plex.PlexMetadata
import ovh.battistella.sillon.data.repository.performers

object HiddenArtists {
    fun matches(name: String, hidden: Set<String>): Boolean = normalize(name) in hidden.mapTo(HashSet(), ::normalize)

    fun credits(credit: String?, hidden: Set<String>): Boolean {
        if (hidden.isEmpty()) return false
        val names = hidden.mapTo(HashSet(), ::normalize)
        return (performers(credit) + listOfNotNull(credit)).any { normalize(it) in names }
    }

    fun keep(tracks: List<PlexMetadata>, hidden: Set<String>): List<PlexMetadata> {
        if (hidden.isEmpty()) return tracks
        return tracks.filterNot { credits(it.originalTitle, hidden) || credits(it.grandparentTitle, hidden) }
    }

    fun primary(credit: String?): String? = performers(credit).firstOrNull()

    private fun normalize(name: String) = name.trim().lowercase()
}
