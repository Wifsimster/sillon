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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val installer: File) : UpdateState
    data class Failed(val release: Release?) : UpdateState
}

class Updater(
    private val feed: UpdateFeed,
    private val client: OkHttpClient,
    private val prefs: DesktopPrefs,
    private val currentVersion: String,
    private val dir: File,
    private val scope: CoroutineScope,
    val canInstall: Boolean = isWindows(),
    private val launch: (List<String>) -> Unit = { ProcessBuilder(it).start() },
    private val executable: () -> String? = { ProcessHandle.current().info().command().orElse(null) },
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
        scope.launch { runCatching { announceInstalled() } }
        periodic = scope.launch {
            while (true) {
                if (prefs.autoUpdate.first()) check(manual = false)
                delay(CHECK_EVERY_MS)
            }
        }
    }

    private suspend fun announceInstalled() {
        val seen = prefs.lastSeenVersion()
        if (seen == currentVersion) return
        prefs.setLastSeenVersion(currentVersion)
        cleanDownloads(keep = null)
        if (seen == null || !Versions.isNewer(currentVersion, seen)) return
        _whatsNew.value = runCatching { feed.tagged(currentVersion) }.getOrNull()?.takeIf { it.notes.isNotBlank() }
    }

    fun dismissWhatsNew() {
        _whatsNew.value = null
    }

    suspend fun check(manual: Boolean) = checking.withLock {
        val before = _state.value
        if (before is UpdateState.Downloading || before is UpdateState.Ready) return@withLock
        if (manual) _state.value = UpdateState.Checking
        val latest = try {
            feed.latest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (manual) UpdateState.Failed(null) else before
            return@withLock
        }
        when {
            latest == null || !Versions.isNewer(latest.version, currentVersion) -> _state.value = UpdateState.UpToDate
            !manual && prefs.skippedVersion() == latest.version -> _state.value = UpdateState.Idle
            else -> {
                _state.value = UpdateState.Available(latest)
                if (canInstall && latest.installerUrl != null && latest.checksumUrl != null) download(latest)
            }
        }
    }

    fun retry() {
        scope.launch { check(manual = true) }
    }

    fun skip() {
        val release = releaseOf(_state.value) ?: return
        _state.value = UpdateState.Idle
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

    private suspend fun download(release: Release) {
        val target = File(dir, "Sillon-${release.version}.msi")
        try {
            val expected = checksum(release)
            if (!(target.isFile && sha256(target) == expected)) {
                _state.value = UpdateState.Downloading(release, 0f)
                fetch(release, target)
                if (sha256(target) != expected) {
                    target.delete()
                    throw IOException("Checksum mismatch")
                }
            }
            cleanDownloads(keep = target)
            _state.value = UpdateState.Ready(release, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(release)
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

    private suspend fun fetch(release: Release, target: File) = withContext(Dispatchers.IO) {
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
                            _state.value = UpdateState.Downloading(release, progress)
                        }
                    }
                }
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not move ${part.name}")
    }

    private fun cleanDownloads(keep: File?) {
        dir.listFiles()?.filter { it != keep && (it.name.endsWith(".msi") || it.name.endsWith(".part") || it.name.endsWith(".cmd")) }
            ?.forEach { it.delete() }
    }

    fun install(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false
        val path = ready.installer.absolutePath
        if (!canInstall || !ready.installer.isFile || path.contains('"') || path.contains('%')) return false
        return runCatching {
            val script = File(dir, "update.cmd")
            script.writeText(installScript(ready.installer, executable()), Charsets.UTF_8)
            launch(listOf("cmd.exe", "/c", "start", "\"\"", "/min", script.absolutePath))
        }.isSuccess
    }

    companion object {
        const val CHECK_EVERY_MS = 6 * 60 * 60_000L
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP = 0.02f

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

        fun installScript(installer: File, executable: String?): String {
            val relaunch = executable?.takeIf {
                it.substringAfterLast('\\').substringAfterLast('/').startsWith("Sillon", ignoreCase = true) && !it.contains('"') && !it.contains('%')
            }
            return buildString {
                append("@echo off\r\n")
                append("chcp 65001 >nul\r\n")
                append("ping -n 3 127.0.0.1 >nul\r\n")
                append("msiexec /i \"").append(installer.absolutePath).append("\" /passive /norestart\r\n")
                if (relaunch != null) append("if not errorlevel 1 start \"\" \"").append(relaunch).append("\"\r\n")
            }
        }
    }
}
