package ovh.battistella.sillon.sonos

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One answer to an SSDP search: where the speaker describes itself, and who it is. */
data class SsdpResponse(
    val location: String,
    val usn: String?,
    val server: String?,
    val household: String?,
) {
    /** `RINCON_…`, the speaker's id in the Sonos topology. */
    val uuid: String? get() = usn?.substringAfter("uuid:", "")?.substringBefore("::")?.takeIf { it.isNotBlank() }

    val isSonos: Boolean get() = uuid?.startsWith("RINCON_") == true || server?.contains("Sonos", ignoreCase = true) == true

    /** `http://host:1400`, where the speaker answers SOAP. */
    val baseUrl: String? get() = baseUrlOf(location)
}

/** SSDP (UPnP discovery): a multicast search on the LAN, read only. */
object Ssdp {
    const val ADDRESS = "239.255.255.250"
    const val PORT = 1900
    const val ZONE_PLAYER = "urn:schemas-upnp-org:device:ZonePlayer:1"

    fun search(target: String = ZONE_PLAYER, waitSeconds: Int = 1): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $ADDRESS:$PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $waitSeconds\r\n" +
            "ST: $target\r\n" +
            "\r\n"

    /** A search answer, or null for anything else (a NOTIFY, a search from another phone, junk). */
    fun parse(datagram: String): SsdpResponse? {
        val lines = datagram.split("\r\n", "\n")
        val status = lines.firstOrNull()?.trim() ?: return null
        if (!status.startsWith("HTTP/1.1 200") && !status.startsWith("HTTP/1.0 200")) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().uppercase() to line.substring(colon + 1).trim()
        }.toMap()
        val location = headers["LOCATION"]?.takeIf { baseUrlOf(it) != null } ?: return null
        return SsdpResponse(
            location = location,
            usn = headers["USN"],
            server = headers["SERVER"],
            household = headers["X-RINCON-HOUSEHOLD"],
        )
    }
}

/** `http://host:port` of a speaker's description URL; only plain http on the LAN. */
internal fun baseUrlOf(location: String): String? {
    val url = location.toHttpUrlOrNull()?.takeIf { it.scheme == "http" } ?: return null
    return "http://${url.host}:${url.port}"
}
