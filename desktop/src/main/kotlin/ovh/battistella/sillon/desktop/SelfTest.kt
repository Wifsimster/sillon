package ovh.battistella.sillon.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import ovh.battistella.sillon.data.plex.PlexJson
import ovh.battistella.sillon.data.plex.PlexResponse
import ovh.battistella.sillon.desktop.platform.DesktopPrefs
import ovh.battistella.sillon.desktop.platform.DesktopStorage
import ovh.battistella.sillon.desktop.player.Mpv
import ovh.battistella.sillon.desktop.player.PlayerEngine
import ovh.battistella.sillon.desktop.ui.components.SillonMark
import ovh.battistella.sillon.desktop.ui.theme.SillonTheme
import ovh.battistella.sillon.desktop.update.Changelog
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.StreamUrls
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

object SelfTest {
    fun run(report: File?): Int {
        val work = Files.createTempDirectory("sillon-self-test").toFile()
        val lines = mutableListOf<String>()
        var failed = false

        fun step(name: String, block: () -> Unit) {
            val error = runCatching(block).exceptionOrNull()
            if (error != null) failed = true
            lines += if (error == null) "ok   $name" else "FAIL $name: $error"
        }

        step("json") {
            val response = PlexJson.decodeFromString(
                PlexResponse.serializer(),
                """{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"1","title":"Sillon","type":"track"}]}}""",
            )
            check(response.container.metadata.single().title == "Sillon")
            check(Changelog.parse("### Features\n\n* one ([abc1234](x))").single().items == listOf("one"))
        }
        step("preferences") {
            runBlocking {
                val prefs = DesktopPrefs(DesktopStorage(File(work, "data"), File(work, "local")))
                prefs.setVolume(0.42f)
                check(prefs.volume() == 0.42f)
            }
        }
        step("playback") {
            val wav = File(work, "tone.wav")
            val rate = 22_050
            val data = ByteArray(rate * 2) { if (it % 40 < 20) 20 else -20 }
            AudioSystem.write(
                AudioInputStream(ByteArrayInputStream(data), AudioFormat(rate.toFloat(), 16, 1, true, false), (data.size / 2).toLong()),
                AudioFileFormat.Type.WAVE,
                wav,
            )
            val engine = PlayerEngine(Mpv.create(PlayerEngine.searchDirs()), audioOutput = "null")
            try {
                engine.play(listOf(PlayableTrack("tone", StreamUrls.ofFile(wav), "Tone", null, null, null, 1_000, null)))
                val deadline = System.currentTimeMillis() + PLAYBACK_TIMEOUT_MS
                while (engine.state.value.positionMs < 100 && System.currentTimeMillis() < deadline) Thread.sleep(20)
                check(engine.state.value.positionMs >= 100) { "mpv did not play: ${engine.state.value.copy(queue = emptyList())}" }
            } finally {
                engine.release()
            }
        }
        step("rendering") {
            val scene = ImageComposeScene(width = 320, height = 200, density = Density(1f)) {
                SillonTheme(darkTheme = true) {
                    Surface {
                        Column {
                            SillonMark(Modifier.size(64.dp), groove = MaterialTheme.colorScheme.onSurface)
                            Text("Sillon", style = MaterialTheme.typography.headlineMedium)
                            Text("Écouter", style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Filled.PlayArrow, null)
                        }
                    }
                }
            }
            try {
                val image = scene.render()
                check(image.width == 320 && image.height == 200)
            } finally {
                scene.close()
            }
        }

        runCatching { work.deleteRecursively() }
        val text = lines.joinToString(System.lineSeparator())
        println(text)
        runCatching { report?.writeText(text + System.lineSeparator()) }
        return if (failed) 1 else 0
    }

    private const val PLAYBACK_TIMEOUT_MS = 15_000L
}
