package ovh.battistella.sillon.data.plex

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexModelsTest {

    @Test
    fun `parses an album's tracks with their part keys`() {
        val json = """
            {"MediaContainer": {"size": 2, "librarySectionID": 3, "Metadata": [
              {"ratingKey": "101", "type": "track", "title": "Intro", "index": 1,
               "parentTitle": "Album", "grandparentTitle": "Artist", "duration": 61000,
               "parentThumb": "/library/metadata/100/thumb/1700000000",
               "Media": [{"id": 9, "audioCodec": "flac", "container": "flac",
                 "Part": [{"id": 7, "key": "/library/parts/7/1700000000/file.flac", "duration": 61000}]}]},
              {"ratingKey": "102", "type": "track", "title": "Feat", "index": 2,
               "grandparentTitle": "Artist", "originalTitle": "Guest", "Media": []}
            ]}}
        """.trimIndent()

        val tracks = PlexJson.decodeFromString(PlexResponse.serializer(), json).container.metadata

        assertEquals(2, tracks.size)
        assertEquals("/library/parts/7/1700000000/file.flac", tracks[0].partKey)
        assertEquals("/library/metadata/100/thumb/1700000000", tracks[0].artwork)
        assertEquals("Artist", tracks[0].trackArtist)
        assertEquals("Guest", tracks[1].trackArtist)
        assertNull(tracks[1].partKey)
    }

    @Test
    fun `keeps only music sections`() {
        val json = """
            {"MediaContainer": {"Directory": [
              {"key": "1", "title": "Movies", "type": "movie"},
              {"key": "3", "title": "Music", "type": "artist"}
            ]}}
        """.trimIndent()

        val sections = PlexJson.decodeFromString(PlexResponse.serializer(), json).container.directories

        assertEquals(listOf("3"), sections.filter { it.isMusic }.map { it.key })
    }

    @Test
    fun `an empty container decodes to empty lists`() {
        val container = PlexJson.decodeFromString(PlexResponse.serializer(), """{"MediaContainer": {"size": 0}}""").container
        assertTrue(container.metadata.isEmpty())
        assertTrue(container.directories.isEmpty())
    }

    @Test
    fun `recognises servers among account resources`() {
        val json = """
            [{"name": "NAS", "clientIdentifier": "abc", "provides": "server", "owned": true,
              "accessToken": "srv-token", "connections": [
                {"protocol": "https", "address": "192.168.1.10", "port": 32400,
                 "uri": "https://192-168-1-10.abc.plex.direct:32400", "local": true, "relay": false}
              ]},
             {"name": "Phone", "clientIdentifier": "def", "provides": "client,player", "connections": []}]
        """.trimIndent()

        val resources = PlexJson.decodeFromString(ListSerializer(PlexResource.serializer()), json)

        assertTrue(resources[0].isServer)
        assertFalse(resources[1].isServer)
        assertEquals("srv-token", resources[0].accessToken)
        assertTrue(resources[0].connections.single().local)
    }
}
