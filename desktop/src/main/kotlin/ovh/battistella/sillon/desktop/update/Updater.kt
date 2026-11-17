package ovh.battistella.sillon.desktop.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ovh.battistella.sillon.desktop.platform.DesktopPrefs
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float, val quiet: Boolean) : UpdateState
    data class Ready(val release: Release, val installer: File) : UpdateState
    data class Failed(val release: Release?) : UpdateState
    data class NotInstalled(val version: String, val pageUrl: String) : UpdateState
}

@Serializable
data class PendingUpdate(val version: String, val file: String, val sha256: String)

class UpdateInstaller(
    private val dir: File,
    private val currentVersion: String,
    private val canInstall: Boolean = isWindows(),
    private val workDir: File = File(System.getProperty("java.io.tmpdir"), "sillon-update"),
    private val launch: (List<String>, File) -> Unit = ::detached,
    private val executable: () -> String? = { ProcessHandle.current().info().command().orElse(null) },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val marker = File(dir, "ready.json")

    fun remember(version: String, installer: File, sha256: String) {
        dir.mkdirs()
        marker.writeText(json.encodeToString(PendingUpdate.serializer(), PendingUpdate(version, installer.name, sha256)))
    }

    fun pending(): Pair<PendingUpdate, File>? {
        val update = runCatching { json.decodeFromString(PendingUpdate.serializer(), marker.readText()) }.getOrNull() ?: return null
        val installer = File(dir, File(update.file).name)
        if (!Versions.isNewer(update.version, currentVersion) || !installer.isFile) return null
        return update to installer
    }

    fun forget() {
        runCatching { workDir.deleteRecursively() }
        dir.listFiles()?.filter { it.name.endsWith(".msi") || it.name.endsWith(".part") || it.name.endsWith(".cmd") || it == marker }
            ?.forEach { it.delete() }
    }

    fun keepOnly(installer: File) {
        dir.listFiles()?.filter { it != installer && it != marker && (it.name.endsWith(".msi") || it.name.endsWith(".part") || it.name.endsWith(".cmd")) }
            ?.forEach { it.delete() }
    }

    fun run(): String? {
        if (!canInstall) return null
        val (update, installer) = pending() ?: return null
        if (runCatching { sha256(installer) }.getOrNull() != update.sha256) {
            forget()
            return null
        }
        return runCatching {
            workDir.deleteRecursively()
            workDir.mkdirs()
            val copy = installer.copyTo(File(workDir, installer.name), overwrite = true)
            val script = File(workDir, "update.cmd")
            val paths = listOf(copy.absolutePath, script.absolutePath)
            check(paths.none { it.contains('"') || it.contains('%') || it.contains('&') })
            script.writeText(installScript(copy, File(workDir, "install.log"), executable()), Charsets.UTF_8)
            launch(listOf("cmd.exe", "/c", script.absolutePath), workDir)
            update.version
        }.getOrNull()
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun installScript(installer: File, log: File, executable: String?): String {
            val relaunch = executable?.takeIf {
                it.substringAfterLast('\\').substringAfterLast('/').startsWith("Sillon", ignoreCase = true) &&
                    it.none { c -> c == '"' || c == '%' || c == '&' }
            }
            return buildString {
                append("@echo off & chcp 65001 >nul & ping -n 3 127.0.0.1 >nul")
                append(" & msiexec /i \"").append(installer.absolutePath).append("\" /passive /norestart /l* \"").append(log.absolutePath).append("\"")
                if (relaunch != null) append(" & (if exist \"").append(relaunch).append("\" start \"\" \"").append(relaunch).append("\")")
                append("\r\n")
            }
        }

        fun detached(command: List<String>, workDir: File) {
            ProcessBuilder(command)
                .directory(workDir)
                .redirectInput(ProcessBuilder.Redirect.from(File("NUL")))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }

        fun installAtStartup(installer: UpdateInstaller, prefs: DesktopPrefs): Boolean = runBlocking {
            if (!prefs.autoUpdate.first()) return@runBlocking false
            val (update, _) = installer.pending() ?: return@runBlocking false
            if (prefs.attemptedVersion() == update.version) return@runBlocking false
            prefs.setAttemptedVersion(update.version)
            installer.run() != null
        }
    }
}

class Updater(
    private val feed: UpdateFeed,
    private val client: OkHttpClient,
    private val prefs: DesktopPrefs,
    private val currentVersion: String,
    private val dir: File,
    private val scope: CoroutineScope,
    val canInstall: Boolean = UpdateInstaller.isWindows(),
    private val installer: UpdateInstaller = UpdateInstaller(dir, currentVersion, canInstall),
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()
    private val _whatsNew = MutableStateFlow<Release?>(null)
    val whatsNew = _whatsNew.asStateFlow()
    private val checking = Mutex()
    private var periodic: Job? = null

    val enabled: Boolean = Versions.isRelease(currentVersion)

    fun start() {
        if (!enabled || periodic != null) return
        periodic = scope.launch {
            runCatching { announceInstalled() }
            while (true) {
                val settled = if (prefs.autoUpdate.first()) check(manual = false) else true
                delay(if (settled) CHECK_EVERY_MS else RETRY_EVERY_MS)
            }
        }
    }

    private suspend fun announceInstalled() {
        val attempted = prefs.attemptedVersion()
        if (attempted != null) {
            if (Versions.isNewer(attempted, currentVersion)) {
                installer.forget()
                _state.value = UpdateState.NotInstalled(attempted, RELEASE_PAGE + attempted)
            } else {
                prefs.setAttemptedVersion(null)
            }
        }
        val seen = prefs.lastSeenVersion()
        if (seen == currentVersion) return
        prefs.setLastSeenVersion(currentVersion)
        installer.forget()
        if (seen == null || !Versions.isNewer(currentVersion, seen)) return
        _whatsNew.value = runCatching { feed.tagged(currentVersion) }.getOrNull()?.takeIf { it.notes.isNotBlank() }
    }

    fun dismissWhatsNew() {
        _whatsNew.value = null
    }

    suspend fun check(manual: Boolean): Boolean = checking.withLock {
        val before = _state.value
        if (before is UpdateState.Downloading || before is UpdateState.Ready) return@withLock true
        if (manual) _state.value = UpdateState.Checking
        val latest = try {
            feed.latest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (manual) UpdateState.Failed(null) else before
            return@withLock false
        }
        when {
            latest == null || !Versions.isNewer(latest.version, currentVersion) -> {
                _state.value = UpdateState.UpToDate
                true
            }
            !manual && (prefs.skippedVersion() == latest.version || prefs.attemptedVersion() == latest.version) -> {
                if (before !is UpdateState.NotInstalled) _state.value = UpdateState.Idle
                true
            }
            !canInstall -> {
                _state.value = UpdateState.Available(latest)
                true
            }
            latest.installerUrl == null || latest.checksumUrl == null -> {
                _state.value = if (manual) UpdateState.Available(latest) else before
                false
            }
            else -> {
                if (manual) prefs.setAttemptedVersion(null)
                download(latest, quiet = !manual)
            }
        }
    }

    fun retry() {
        scope.launch { check(manual = true) }
    }

    fun skip() {
        val release = releaseOf(_state.value) ?: return
        _state.value = UpdateState.Idle
        installer.forget()
        scope.launch { prefs.setSkippedVersion(release.version) }
    }

    fun later() {
        _state.value = UpdateState.Idle
    }

    private fun releaseOf(state: UpdateState): Release? = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Ready -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    }

    private suspend fun download(release: Release, quiet: Boolean): Boolean {
        val before = _state.value
        val target = File(dir, "Sillon-${release.version}.msi")
        return try {
            val expected = checksum(release)
            val have = withContext(Dispatchers.IO) { target.isFile && UpdateInstaller.sha256(target) == expected }
            if (!have) {
                _state.value = UpdateState.Downloading(release, 0f, quiet)
                fetch(release, target, quiet)
                if (withContext(Dispatchers.IO) { UpdateInstaller.sha256(target) } != expected) {
                    target.delete()
                    throw IOException("Checksum mismatch")
                }
            }
            installer.keepOnly(target)
            installer.remember(release.version, target, expected)
            _state.value = UpdateState.Ready(release, target)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (quiet) before.takeUnless { it is UpdateState.Downloading } ?: UpdateState.Idle else UpdateState.Failed(release)
            false
        }
    }

    private suspend fun checksum(release: Release): String = withContext(Dispatchers.IO) {
        val url = release.checksumUrl ?: throw IOException("No checksum")
        client.newCall(Request.Builder().url(url).header("User-Agent", "Sillon").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val line = response.body?.string().orEmpty().lines().firstOrNull { it.trim().endsWith(UpdateFeed.INSTALLER) }
            line?.trim()?.substringBefore(' ')?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                ?: throw IOException("No checksum for the installer")
        }
    }

    private suspend fun fetch(release: Release, target: File, quiet: Boolean) = withContext(Dispatchers.IO) {
        val url = release.installerUrl ?: throw IOException("No installer")
        dir.mkdirs()
        val part = File(dir, target.name + ".part")
        client.newCall(Request.Builder().url(url).header("User-Agent", "Sillon").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: release.installerSize
            var written = 0L
            var shown = 0f
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val progress = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (progress - shown >= PROGRESS_STEP) {
                            shown = progress
                            _state.value = UpdateState.Downloading(release, progress, quiet)
                        }
                    }
                }
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not move ${part.name}")
    }

    fun install(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false
        runBlocking { prefs.setAttemptedVersion(ready.release.version) }
        return installer.run() != null
    }

    companion object {
        const val CHECK_EVERY_MS = 6 * 60 * 60_000L
        const val RETRY_EVERY_MS = 10 * 60_000L
        const val RELEASE_PAGE = "https://github.com/Wifsimster/sillon/releases/tag/v"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP = 0.02f
    }
}
