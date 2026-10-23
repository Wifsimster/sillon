package ovh.battistella.sillon.playback

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.browse.MediaBrowser
import android.os.Looper
import android.os.Process
import android.service.media.MediaBrowserService
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Android Auto browses Sillon the legacy way (a framework MediaBrowser). Media3 then waits for
 * `onGetLibraryRoot` on the main thread: a root that needs the main thread to complete never
 * comes, and the car shows a spinner forever. These tests drive the real service as the car does.
 */
@RunWith(RobolectricTestRunner::class)
class AutoBrowseTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `Android Auto gets the library root without blocking the main thread`() = assertBrowses(AUTO)

    @Test
    fun `the desktop head unit simulator gets the library root too`() = assertBrowses(SIMULATOR)

    private fun assertBrowses(carPackage: String) {
        val service = ComponentName(app, PlaybackService::class.java)
        val binder = Robolectric.buildService(PlaybackService::class.java).create().get()
            .onBind(Intent(MediaBrowserService.SERVICE_INTERFACE))
        shadowOf(app as android.app.Application).setComponentNameAndServiceForBindService(service, binder)
        shadowOf(app.packageManager).setPackagesForUid(Process.myUid(), carPackage, app.packageName)

        val car = object : ContextWrapper(app) {
            override fun getPackageName(): String = carPackage
        }
        val connected = AtomicBoolean(false)
        val failed = AtomicBoolean(false)
        lateinit var browser: MediaBrowser
        browser = MediaBrowser(
            car,
            service,
            object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() = connected.set(true)

                override fun onConnectionFailed() = failed.set(true)
            },
            null,
        )
        browser.connect()

        val blocked = awaitOnMain { connected.get() || failed.get() }
        assertFalse("the main thread was blocked waiting for onGetLibraryRoot", blocked)
        assertFalse("the car was refused", failed.get())
        assertTrue("the car never connected", connected.get())
        assertEquals(BrowseIds.ROOT, browser.root)

        var children: List<MediaBrowser.MediaItem>? = null
        browser.subscribe(
            browser.root,
            object : MediaBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, items: List<MediaBrowser.MediaItem>) {
                    children = items
                }
            },
        )
        assertFalse("the main thread was blocked loading the root's children", awaitOnMain { children != null })
        assertNotNull("the root's children never came", children)
        assertEquals(
            listOf(BrowseNode.FiveStars, BrowseNode.Artists, BrowseNode.Albums, BrowseNode.Playlists, BrowseNode.Radios)
                .map(BrowseIds::of),
            children!!.map { it.mediaId },
        )
    }

    /**
     * Runs the main looper until [done] or [TIMEOUT_MS]. Returns whether the main thread got stuck
     * in a task: a watchdog then interrupts it, which Media3 takes as a failed root.
     */
    private fun awaitOnMain(done: () -> Boolean): Boolean {
        val main = Thread.currentThread()
        val finished = AtomicBoolean(false)
        val inTask = AtomicBoolean(false)
        val stuck = AtomicBoolean(false)
        val watchdog = thread(isDaemon = true) {
            try {
                Thread.sleep(TIMEOUT_MS)
            } catch (_: InterruptedException) {
                return@thread
            }
            if (!finished.get() && inTask.get()) {
                stuck.set(true)
                main.interrupt()
            }
        }
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        try {
            while (!done() && !stuck.get() && System.currentTimeMillis() < deadline) {
                inTask.set(true)
                shadowOf(Looper.getMainLooper()).idle()
                inTask.set(false)
                if (!done()) Thread.sleep(POLL_MS)
            }
        } catch (_: InterruptedException) {
            // The watchdog's interrupt.
        } finally {
            finished.set(true)
            watchdog.interrupt()
            Thread.interrupted()
        }
        // Lets what the interrupt unblocked finish its task.
        shadowOf(Looper.getMainLooper()).idle()
        return stuck.get()
    }

    private companion object {
        const val AUTO = "com.google.android.projection.gearhead"
        const val SIMULATOR = "com.google.android.autosimulator"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 20L
    }
}
