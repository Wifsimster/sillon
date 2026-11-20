package ovh.battistella.sillon.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ovh.battistella.sillon.desktop.player.EngineListener
import ovh.battistella.sillon.desktop.player.EngineState
import ovh.battistella.sillon.desktop.player.Mpv
import ovh.battistella.sillon.desktop.player.PlayerEngine
import ovh.battistella.sillon.desktop.player.RepeatMode
import ovh.battistella.sillon.desktop.player.Transition
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.StreamUrls
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

class PlayerEngineTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var engine: PlayerEngine
    private val transitions = CopyOnWriteArrayList<Transition>()
    private val skipped = mutableSetOf<String>()
    private val seenAtTransition = CopyOnWriteArrayList<String?>()
    private val failed = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        assumeTrue("libmpv is not installed", Mpv.isAvailable(PlayerEngine.searchDirs()))
        engine = PlayerEngine(
            mpv = Mpv.create(PlayerEngine.searchDirs()),
            listener = object : EngineListener {
                override fun onTransition(transition: Transition) {
                    seenAtTransition.add(engine.state.value.current?.ratingKey)
                    transitions.add(transition)
                }

                override fun onFailed(track: PlayableTrack) {
                    failed.add(track.ratingKey)
                }
            },
            skip = { it.ratingKey in skipped },
            audioOutput = "null",
        )
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.release()
    }

    private fun track(key: String, seconds: Double = 1.0, gainDb: Float? = null): PlayableTrack {
        val file = File(folder.root, "$key with space é.wav")
        val rate = 44_100
        val frames = (rate * seconds).toInt()
        val data = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val sample = (sin(2 * PI * 440 * i / rate) * 8_000).toInt()
            data[2 * i] = (sample and 0xFF).toByte()
            data[2 * i + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(data), format, frames.toLong()), AudioFileFormat.Type.WAVE, file)
        return PlayableTrack(key, StreamUrls.ofFile(file), "Title $key", "Artist", "Album", 1, (seconds * 1000).toLong(), null, gainDb)
    }

    private fun await(what: String, timeoutMs: Long = 10_000, condition: (EngineState) -> Boolean): EngineState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = engine.state.value
            if (condition(state)) return state
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what: ${engine.state.value.copy(queue = emptyList())} keys=${keys()}")
    }

    private fun keys() = engine.state.value.queue.map { it.ratingKey }

    @Test
    fun `plays the queue in order then stops at the end`() {
        engine.play(listOf(track("a"), track("b"), track("c")))
        await("a playing") { it.playing && it.index == 0 && it.positionMs > 100 }
        await("b") { it.index == 1 }
        await("c") { it.index == 2 }
        val end = await("the end") { !it.playing && it.index == 0 }
        assertEquals(0, end.positionMs)
        assertEquals(listOf("a", "b", "c"), end.queue.map { it.ratingKey })
        val automatic = transitions.filter { it.automatic }.map { it.previous?.ratingKey to it.current?.ratingKey }
        assertEquals(listOf("a" to "b", "b" to "c", "c" to null), automatic)
        assertEquals(listOf("a", "b", "c", "a"), seenAtTransition.toList())
        assertEquals(listOf("a", "b", "c", null), transitions.map { it.current?.ratingKey })
    }

    @Test
    fun `starts at the chosen track and pauses`() {
        engine.play(listOf(track("a", 5.0), track("b", 5.0), track("c", 5.0)), startIndex = 1)
        await("b playing") { it.playing && it.index == 1 && it.positionMs > 100 }
        engine.playPause()
        val paused = await("paused") { !it.playing }
        assertEquals(1, paused.index)
        Thread.sleep(400)
        assertTrue(engine.state.value.positionMs - paused.positionMs < 200)
        engine.playPause()
        await("resumed") { it.playing }
    }

    @Test
    fun `next previous jump and seek`() {
        engine.play(listOf(track("a", 8.0), track("b", 8.0), track("c", 8.0)))
        await("a") { it.playing && it.index == 0 }
        engine.next()
        await("b") { it.playing && it.index == 1 }
        engine.jumpTo(2)
        await("c") { it.playing && it.index == 2 }
        engine.seekTo(5_000)
        await("seeked") { it.positionMs in 4_900..6_500 }
        engine.previous()
        await("restart of c") { it.index == 2 && it.positionMs < 1_500 }
        engine.previous()
        await("back to b") { it.index == 1 }
        val seen = transitions.joinToString { "${it.previous?.ratingKey}@${it.previousPositionMs}->${it.current?.ratingKey} auto=${it.automatic}" }
        assertFalse("failed=$failed transitions=[$seen]", transitions.any { it.automatic })
    }

    @Test
    fun `editing the queue keeps the playing track`() {
        engine.play(listOf(track("a", 20.0), track("b", 20.0), track("c", 20.0), track("d", 20.0)), startIndex = 1)
        await("b") { it.playing && it.index == 1 && it.positionMs > 100 }
        engine.move(3, 0)
        await("d first") { keys() == listOf("d", "a", "b", "c") && it.index == 2 }
        engine.move(0, 3)
        await("d last") { keys() == listOf("a", "b", "c", "d") && it.index == 1 }
        engine.removeAt(0)
        await("a removed") { keys() == listOf("b", "c", "d") && it.index == 0 }
        engine.insertNext(listOf(track("x", 20.0), track("y", 20.0)))
        await("x y next") { keys() == listOf("b", "x", "y", "c", "d") && it.index == 0 }
        engine.append(listOf(track("z", 20.0)))
        await("z last") { keys().last() == "z" }
        engine.replaceUpcoming(listOf(track("r1", 20.0), track("b", 20.0), track("r2", 20.0)))
        await("radio") { keys() == listOf("b", "r1", "r2") && it.index == 0 }
        assertTrue(engine.state.value.playing)
        assertTrue(engine.state.value.positionMs > 100)
        engine.next()
        await("r1 really plays") { it.index == 1 && it.playing && it.current?.ratingKey == "r1" && it.positionMs > 100 }
    }

    @Test
    fun `removing the playing track moves on`() {
        engine.play(listOf(track("a", 20.0), track("b", 20.0), track("c", 20.0)))
        await("a") { it.playing && it.index == 0 && it.positionMs > 100 }
        engine.removeAt(0)
        await("b plays") { keys() == listOf("b", "c") && it.index == 0 && it.playing && it.positionMs > 100 }
    }

    @Test
    fun `shuffle keeps the playing track and comes back to the order`() {
        val tracks = ('a'..'h').map { track(it.toString(), 20.0) }
        engine.play(tracks, startIndex = 3)
        await("d") { it.playing && it.index == 3 && it.positionMs > 100 }
        engine.setShuffle(true)
        val shuffled = await("shuffled") { it.shuffle }
        assertEquals("d", shuffled.current?.ratingKey)
        assertEquals(0, shuffled.index)
        assertEquals(tracks.map { it.ratingKey }.toSet(), shuffled.queue.map { it.ratingKey }.toSet())
        assertTrue(shuffled.playing)
        engine.setShuffle(false)
        val back = await("unshuffled") { !it.shuffle }
        assertEquals(tracks.map { it.ratingKey }, back.queue.map { it.ratingKey })
        assertEquals(3, back.index)
        assertTrue(back.playing)
        engine.next()
        await("e really plays") { it.index == 4 && it.playing && it.positionMs > 100 }
        engine.previous()
        await("back on d") { it.index == 3 && it.playing && it.current?.ratingKey == "d" }
    }

    @Test
    fun `shuffled start plays every track once`() {
        val tracks = ('a'..'f').map { track(it.toString(), 20.0) }
        engine.play(tracks, shuffle = true)
        val state = await("playing") { it.playing && it.positionMs > 100 }
        assertTrue(state.shuffle)
        assertEquals(0, state.index)
        assertEquals(tracks.map { it.ratingKey }.sorted(), state.queue.map { it.ratingKey }.sorted())
    }

    @Test
    fun `skipped tracks are passed when the queue moves on by itself, not when chosen`() {
        skipped += "b"
        engine.play(listOf(track("a"), track("b", 5.0), track("c", 5.0)))
        await("c after a") { it.index == 2 && it.playing }
        engine.jumpTo(1)
        await("b chosen") { it.index == 1 && it.playing && it.positionMs > 100 }
        engine.jumpTo(0)
        await("a") { it.index == 0 && it.playing }
        engine.next()
        await("next skips b") { it.index == 2 && it.playing }
    }

    @Test
    fun `repeat all loops the queue`() {
        engine.play(listOf(track("a"), track("b")))
        engine.setRepeat(RepeatMode.ALL)
        await("b") { it.index == 1 }
        await("a again") { it.index == 0 && it.playing }
        assertEquals(RepeatMode.ALL, engine.state.value.repeat)
    }

    @Test
    fun `a restored queue waits paused at its position`() {
        engine.play(listOf(track("a", 10.0), track("b", 10.0)), startIndex = 1, positionMs = 4_000, paused = true)
        val restored = await("restored") { it.index == 1 && it.queue.size == 2 }
        Thread.sleep(300)
        assertFalse(engine.state.value.playing)
        assertEquals(4_000, restored.positionMs)
        engine.playPause()
        await("resumes at 4 s") { it.playing && it.positionMs in 3_900..6_000 }
    }

    @Test
    fun `a restored queue with no position starts on its own track`() {
        skipped += "a"
        engine.play(listOf(track("a", 10.0), track("b", 10.0), track("c", 10.0)), startIndex = 1, paused = true)
        await("restored") { it.index == 1 && it.queue.size == 3 }
        Thread.sleep(600)
        assertEquals(1, engine.state.value.index)
        engine.playPause()
        val playing = await("b plays") { it.playing && it.positionMs > 100 }
        assertEquals("b", playing.current?.ratingKey)
    }

    @Test
    fun `a missing file fails and the queue goes on`() {
        val failed = CopyOnWriteArrayList<String>()
        engine.release()
        engine = PlayerEngine(
            mpv = Mpv.create(PlayerEngine.searchDirs()),
            listener = object : EngineListener {
                override fun onFailed(track: PlayableTrack) {
                    failed.add(track.ratingKey)
                }
            },
            audioOutput = "null",
        )
        val missing = PlayableTrack("gone", StreamUrls.ofFile(File(folder.root, "gone.flac")), "Gone", null, null, null, 1000, null)
        val b = track("b", 5.0)
        engine.play(listOf(missing, b))
        await("b plays") { it.index == 1 && it.playing && it.positionMs > 100 }
        assertEquals(listOf("gone"), failed.toList())
        engine.replace(missing, track("fixed", 5.0), play = true)
        val fixed = await("the replacement plays") { it.index == 0 && it.playing && it.positionMs > 100 }
        assertEquals(listOf("fixed", "b"), fixed.queue.map { it.ratingKey })
    }

    @Test
    fun `the last track failing ends the queue`() {
        val missing = PlayableTrack("gone", StreamUrls.ofFile(File(folder.root, "gone.flac")), "Gone", null, null, null, 1000, null)
        engine.play(listOf(track("a"), missing))
        await("the end") { !it.playing && it.index == 0 && transitions.any { t -> t.current == null } }
        engine.playPause()
        await("a again") { it.playing && it.index == 0 && it.positionMs > 100 }
    }
}
