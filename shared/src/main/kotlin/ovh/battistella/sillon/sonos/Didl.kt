package ovh.battistella.sillon.sonos

/** A track as the Sonos queue holds it: the URL the speaker fetches, and what it shows. */
data class SonosTrack(
    /** Sillon's key for the track (Plex rating key, Jellyfin item id). */
    val key: String,
    val uri: String,
    val didl: String,
)

/** DIDL-Lite: the metadata the Sonos app and the speaker show for a URL in the queue. */
object Didl {
    fun track(
        key: String,
        uri: String,
        mimeType: String,
        title: String,
        artist: String?,
        album: String?,
        artUri: String?,
    ): String = buildString {
        append("<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ")
        append("xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" ")
        append("xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\">")
        append("<item id=\"").append(Soap.escape("sillon:$key")).append("\" parentID=\"-1\" restricted=\"true\">")
        append("<dc:title>").append(Soap.escape(title)).append("</dc:title>")
        artist?.takeIf { it.isNotBlank() }?.let { append("<dc:creator>").append(Soap.escape(it)).append("</dc:creator>") }
        album?.takeIf { it.isNotBlank() }?.let { append("<upnp:album>").append(Soap.escape(it)).append("</upnp:album>") }
        artUri?.takeIf { it.startsWith("http") }?.let {
            append("<upnp:albumArtURI>").append(Soap.escape(it)).append("</upnp:albumArtURI>")
        }
        append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
        append("<res protocolInfo=\"http-get:*:").append(mimeType).append(":*\">").append(Soap.escape(uri)).append("</res>")
        append("</item></DIDL-Lite>")
    }
}
