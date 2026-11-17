package ovh.battistella.sillon.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ovh.battistella.sillon.desktop.platform.DesktopPrefs
import ovh.battistella.sillon.desktop.platform.DesktopStorage
import ovh.battistella.sillon.desktop.update.Changelog
import ovh.battistella.sillon.desktop.update.UpdateFeed
import ovh.battistella.sillon.desktop.update.UpdateState
import ovh.battistella.sillon.desktop.update.Updater
import ovh.battistella.sillon.desktop.update.Versions
import java.io.File
import java.security.MessageDigest

class UpdaterTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installer = ByteArray(300_000) { (it % 251).toByte() }
    private var published = "1.27.0"
    private var checksum = sha256(installer)
    private var withInstaller = true
    private val launched = mutableListOf<List<String>>()
    private lateinit var prefs: DesktopPrefs

    private val notes = """
        ## [1.27.0](https://github.com/Wifsimster/sillon/compare/v1.26.0...v1.27.0) (2026-11-17)

        ### Features

        * **desktop:** updates itself, and says what changed ([#46](https://github.com/Wifsimster/sillon/issues/46)) ([abc1234](https://github.com/Wifsimster/sillon/commit/abc1234def))
        * sleeve wear on Windows ([1e460db](https://github.com/Wifsimster/sillon/commit/1e460db))

        ### Bug Fixes

        * the queue is saved on the right track
    """.trimIndent()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun releaseJson(version: String): String {
        val assets = buildList {
            if (withInstaller) {
                add("""{"name":"Sillon-windows-x64.msi","browser_download_url":"${server.url("/download/Sillon-windows-x64.msi")}","size":${installer.size}}""")
                add("""{"name":"SHA256SUMS-windows.txt","browser_download_url":"${server.url("/download/SHA256SUMS-windows.txt")}","size":89}""")
            }
            add("""{"name":"app-release.apk","browser_download_url":"${server.url("/download/app-release.apk")}","size":1}""")
        }
        val body = notes.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"tag_name":"v$version","body":"$body","html_url":"https://example.org/releases/v$version","draft":false,"prerelease":false,"assets":[${assets.joinToString(",")}]}"""
    }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/repo/releases/latest" -> MockResponse().setBody(releaseJson(published))
                "/repo/releases/tags/v1.27.0" -> MockResponse().setBody(releaseJson("1.27.0"))
                "/download/Sillon-windows-x64.msi" -> MockResponse().setBody(Buffer().write(installer))
                "/download/SHA256SUMS-windows.txt" -> MockResponse().setBody("$checksum  Sillon-windows-x64.msi\n")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        prefs = DesktopPrefs(DesktopStorage(folder.newFolder("data"), folder.newFolder("local")))
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun updater(current: String, canInstall: Boolean = true, executable: String? = "C:\\Users\\Zoé\\AppData\\Local\\Sillon\\Sillon.exe") = Updater(
        feed = UpdateFeed(OkHttpClient(), server.url("/repo/")),
        client = OkHttpClient(),
        prefs = prefs,
        currentVersion = current,
        dir = File(folder.root, "updates"),
        scope = scope,
        canInstall = canInstall,
        launch = { launched += it },
        executable = { executable },
    )

    @Test
    fun `versions compare by number, not by text`() {
        assertTrue(Versions.isNewer("1.27.0", "1.26.0"))
        assertTrue(Versions.isNewer("v1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("2.0", "1.99.99"))
        assertFalse(Versions.isNewer("1.26.0", "1.26.0"))
        assertFalse(Versions.isNewer("1.25.9", "1.26.0"))
        assertFalse(Versions.isNewer("latest", "1.26.0"))
        assertTrue(Versions.isRelease("1.26.0"))
        assertFalse(Versions.isRelease("0.0.0"))
        assertFalse(Versions.isRelease("1.27.0-beta.1"))
    }

    @Test
    fun `release notes become plain sections`() {
        val sections = Changelog.parse(notes)
        assertEquals(listOf("Features", "Bug Fixes"), sections.map { it.title })
        assertEquals(
            listOf("desktop: updates itself, and says what changed", "sleeve wear on Windows"),
            sections[0].items,
        )
        assertEquals(listOf("the queue is saved on the right track"), sections[1].items)
        assertEquals(emptyList<Any>(), Changelog.parse(""))
    }

    @Test
    fun `a newer version is downloaded, checked and offered`() = runBlocking {
        val updater = updater("1.26.0")
        updater.check(manual = false)
        val ready = updater.state.value as UpdateState.Ready
        assertEquals("1.27.0", ready.release.version)
        assertEquals(installer.size.toLong(), ready.installer.length())
        assertEquals(checksum, Updater.sha256(ready.installer))
        assertTrue(updater.install())
        val command = launched.single()
        assertEquals(listOf("cmd.exe", "/c", "start"), command.take(3))
        val script = File(command.last()).readText()
        assertTrue(script.contains("msiexec /i \"${ready.installer.absolutePath}\" /passive /norestart"))
        assertTrue(script.contains("start \"\" \"C:\\Users\\Zoé\\AppData\\Local\\Sillon\\Sillon.exe\""))
    }

    @Test
    fun `an installer that does not match its checksum is thrown away`() = runBlocking {
        checksum = "0".repeat(64)
        val updater = updater("1.26.0")
        updater.check(manual = false)
        assertTrue(updater.state.value is UpdateState.Failed)
        assertFalse(updater.install())
        assertTrue(launched.isEmpty())
        assertEquals(emptyList<String>(), File(folder.root, "updates").list().orEmpty().filter { it.endsWith(".msi") })
    }

    @Test
    fun `nothing is offered when up to date`() = runBlocking {
        val updater = updater("1.27.0")
        updater.check(manual = true)
        assertEquals(UpdateState.UpToDate, updater.state.value)
    }

    @Test
    fun `a skipped version stays quiet until asked for`() = runBlocking {
        prefs.setSkippedVersion("1.27.0")
        val updater = updater("1.26.0")
        updater.check(manual = false)
        assertEquals(UpdateState.Idle, updater.state.value)
        updater.check(manual = true)
        assertTrue(updater.state.value is UpdateState.Ready)
    }

    @Test
    fun `where it cannot install, the version is only announced`() = runBlocking {
        val updater = updater("1.26.0", canInstall = false)
        updater.check(manual = false)
        assertTrue(updater.state.value is UpdateState.Available)
        assertFalse(updater.install())
        withInstaller = false
        val windows = updater("1.26.0")
        windows.check(manual = false)
        assertTrue(windows.state.value is UpdateState.Available)
    }

    @Test
    fun `a failed check leaves things as they were`() = runBlocking {
        val updater = updater("1.26.0")
        server.shutdown()
        updater.check(manual = false)
        assertEquals(UpdateState.Idle, updater.state.value)
        updater.check(manual = true)
        assertEquals(UpdateState.Failed(null), updater.state.value)
    }

    @Test
    fun `the first start after an update tells what is new, once`() = runBlocking {
        prefs.setLastSeenVersion("1.26.0")
        prefs.setAutoUpdate(false)
        val updater = updater("1.27.0")
        updater.start()
        val deadline = System.currentTimeMillis() + 5_000
        while (updater.whatsNew.value == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals("1.27.0", updater.whatsNew.value?.version)
        assertEquals("1.27.0", prefs.lastSeenVersion())
        updater.dismissWhatsNew()
        val again = updater("1.27.0")
        again.start()
        Thread.sleep(300)
        assertNull(again.whatsNew.value)
    }

    @Test
    fun `the install script never relaunches something that is not Sillon`() {
        val msi = File("C:\\cache\\Sillon-1.27.0.msi")
        assertFalse(Updater.installScript(msi, "C:\\Program Files\\Java\\bin\\java.exe").contains("start \"\""))
        assertFalse(Updater.installScript(msi, null).contains("start \"\""))
        assertFalse(Updater.installScript(msi, "C:\\x\\Sillon\" & calc & \".exe").contains("calc"))
    }
}
