package ovh.battistella.sillon.data.repository

import ovh.battistella.sillon.data.plex.PlexMetadata

fun List<PlexMetadata>.byLatestListen(): List<PlexMetadata> =
    sortedByDescending { it.lastViewedAt ?: 0L }
