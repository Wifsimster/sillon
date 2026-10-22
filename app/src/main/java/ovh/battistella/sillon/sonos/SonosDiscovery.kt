package ovh.battistella.sillon.sonos

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Finds the Sonos speakers on the Wi-Fi: an SSDP search for ZonePlayers, then the household's
 * groups from the first speaker that answers. Read only: nothing is played or changed.
 */
class SonosDiscovery(private val context: Context, private val client: SonosClient) {
    suspend fun discover(listenMs: Long = LISTEN_MS): List<SonosGroup> = withContext(Dispatchers.IO) {
        val speakers = search(listenMs)
        if (speakers.isEmpty()) return@withContext emptyList()
        speakers.firstNotNullOfOrNull { base ->
            runCatching { client.zoneGroups(base) }
                .onFailure { Log.w(TAG, "topology from a speaker failed: ${it.message}") }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun search(listenMs: Long): List<String> {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        // Some phones drop multicast answers without the lock.
        val lock = runCatching {
            wifi?.createMulticastLock("sillon-sonos")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = RECEIVE_TIMEOUT_MS
                val message = Ssdp.search().toByteArray(Charsets.US_ASCII)
                val group = InetAddress.getByName(Ssdp.ADDRESS)
                repeat(SEARCHES) { socket.send(DatagramPacket(message, message.size, group, Ssdp.PORT)) }
                val found = LinkedHashMap<String, String>()
                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + listenMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val response = Ssdp.parse(String(packet.data, 0, packet.length, Charsets.US_ASCII)) ?: continue
                    if (!response.isSonos) continue
                    val base = response.baseUrl ?: continue
                    found[response.uuid ?: base] = base
                }
                return found.values.toList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP search failed: ${e.message}")
            return emptyList()
        } finally {
            runCatching { lock?.release() }
        }
    }

    private companion object {
        const val TAG = "SillonSonos"
        const val LISTEN_MS = 2_500L
        const val RECEIVE_TIMEOUT_MS = 400
        const val SEARCHES = 2
    }
}
