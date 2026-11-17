package ovh.battistella.sillon.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexApi
import ovh.battistella.sillon.data.plex.PlexDevice
import ovh.battistella.sillon.data.plex.ServerEndpoint
import org.jetbrains.skia.Bitmap
import ovh.battistella.sillon.desktop.ui.player.Waveform
import ovh.battistella.sillon.desktop.ui.player.WaveformShape
import ovh.battistella.sillon.desktop.ui.theme.SillonTheme

class WaveformTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `levels are asked for the track's audio stream`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"MediaContainer":{"Metadata":[{"ratingKey":"42","Media":[{"Part":[{"key":"/p","Stream":[
                {"id":900,"streamType":4},{"id":771,"streamType":2,"codec":"flac"}]}]}]}]}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"MediaContainer":{"size":3,"Level":[{"v":-31.5},{"v":-12.0},{"v":-20.25}]}}"""))
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "tok")
        val api = PlexApi(OkHttpClient(), PlexDevice("Sillon", "1", "11", "PC", "Windows"))

        assertEquals(listOf(-31.5f, -12.0f, -20.25f), api.loudnessLevels("client", endpoint, "42", 240))
        server.takeRequest()
        val levels = server.takeRequest().requestUrl!!
        assertEquals("/library/streams/771/levels", levels.encodedPath)
        assertEquals("240", levels.queryParameter("subsample"))
    }

    @Test
    fun `a track with no audio stream id has no levels`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"MediaContainer":{"Metadata":[{"ratingKey":"42","Media":[{"Part":[{"key":"/p"}]}]}]}}"""))
        val endpoint = ServerEndpoint(server.url("/").toString().trimEnd('/'), "tok")
        val api = PlexApi(OkHttpClient(), PlexDevice("Sillon", "1", "11", "PC", "Windows"))
        assertEquals(emptyList<Float>(), api.loudnessLevels("client", endpoint, "42", 240))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `heights follow the loudness, between a floor and one`() {
        val heights = Waveform.heights(listOf(-60f, -30f, -10f, -10f, -30f, -60f), count = 6)
        assertEquals(6, heights.size)
        assertTrue(heights.all { it in 0.05f..1f })
        assertEquals(1f, heights[2], 0.001f)
        assertTrue(heights[1] < heights[2] && heights[0] < heights[1])
        assertEquals(heights[0], heights[5], 0.001f)
    }

    @Test
    fun `any number of levels is spread over the bars asked for`() {
        assertEquals(240, Waveform.heights(List(1000) { -20f - it % 30 }).size)
        assertEquals(240, Waveform.heights(List(50) { -20f - it % 30 }).size)
        assertEquals(emptyList<Float>(), Waveform.heights(emptyList()))
        assertEquals(emptyList<Float>(), Waveform.heights(listOf(-10f)))
        assertTrue(Waveform.heights(listOf(Float.NEGATIVE_INFINITY, -10f, -20f), count = 3).all { it.isFinite() })
    }

    @Test
    fun `the wave is drawn brighter up to the playhead`() {
        val levels = List(600) { -38f + 26f * kotlin.math.abs(kotlin.math.sin(it / 37.0)).toFloat() + 6f * kotlin.math.sin(it / 5.0).toFloat() }
        val scene = ImageComposeScene(width = 520, height = 48, density = Density(1f)) {
            SillonTheme(darkTheme = true) {
                Surface { WaveformShape(Waveform.heights(levels), 0.6f, Modifier.fillMaxSize()) }
            }
        }
        try {
            val image = scene.render()
            System.getProperty("sillon.render.dir")?.let { dir ->
                java.io.File(dir, "waveform.png").writeBytes(image.encodeToData()!!.bytes)
            }
            val bitmap = Bitmap.makeFromImage(image)
            fun brightness(x: Int, y: Int) = bitmap.getColor(x, y).let { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) }
            assertTrue(brightness(100, 24) > brightness(450, 24))
            assertTrue(brightness(450, 24) > brightness(450, 1))
        } finally {
            scene.close()
        }
    }
}
