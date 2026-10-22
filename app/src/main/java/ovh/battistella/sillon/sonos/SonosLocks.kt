package ovh.battistella.sillon.sonos

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Keeps Wi-Fi awake and the CPU running while the music plays on Sonos. The phone's own player is
 * paused then, so its network wake mode is off; without these, the polls stall or fail with the
 * screen off, and after a few failures the session would be dropped while the speaker plays on.
 */
class SonosLocks(context: Context) {
    private val app = context.applicationContext

    @Suppress("DEPRECATION") // HIGH_PERF is what Media3's own network wake mode takes.
    private val wifi: WifiManager.WifiLock? = runCatching {
        app.getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "sillon:sonos")
            ?.apply { setReferenceCounted(false) }
    }.getOrNull()

    private val cpu: PowerManager.WakeLock? = runCatching {
        app.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sillon:sonos")
            ?.apply { setReferenceCounted(false) }
    }.getOrNull()

    /** Holds both locks while [playing], releases them otherwise. */
    fun hold(playing: Boolean) {
        runCatching {
            if (playing) {
                wifi?.takeUnless { it.isHeld }?.acquire()
                cpu?.takeUnless { it.isHeld }?.acquire(MAX_HOLD_MS)
            } else {
                wifi?.takeIf { it.isHeld }?.release()
                cpu?.takeIf { it.isHeld }?.release()
            }
        }
    }

    private companion object {
        /** A safety net: renewed at every play, so a forgotten lock dies on its own. */
        const val MAX_HOLD_MS = 6 * 60 * 60 * 1000L
    }
}
