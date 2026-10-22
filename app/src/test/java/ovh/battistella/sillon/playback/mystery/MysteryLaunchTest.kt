package ovh.battistella.sillon.playback.mystery

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.sillon.playback.PlayableTrack
import java.io.IOException

class MysteryLaunchTest {
    private val mystery = Mystery(
        albumKey = "album",
        tracks = (1..4).map {
            MysteryTrack(PlayableTrack("$it", "https://srv.example/$it", "Song $it", null, null, it, null, null), 1, it)
        },
    )

    @Test
    fun `a group joined during the draw stops the surprise before it plays`() = runTest {
        var together = false
        val played = mutableListOf<Mystery>()

        val problem = drawThenPlay(
            draw = { together = true; mystery },
            listeningTogether = { together },
            play = { played += it },
        )

        assertEquals(MysteryProblem.LISTENING_TOGETHER, problem)
        assertTrue(played.isEmpty())
    }

    @Test
    fun `already listening together, nothing is drawn`() = runTest {
        var drew = false

        val problem = drawThenPlay(draw = { drew = true; mystery }, listeningTogether = { true }, play = {})

        assertEquals(MysteryProblem.LISTENING_TOGETHER, problem)
        assertTrue(!drew)
    }

    @Test
    fun `alone, the drawn album plays`() = runTest {
        val played = mutableListOf<Mystery>()

        val problem = drawThenPlay(draw = { mystery }, listeningTogether = { false }, play = { played += it })

        assertNull(problem)
        assertEquals(listOf(mystery), played)
    }

    @Test
    fun `a failed draw says why`() = runTest {
        assertEquals(
            MysteryProblem.NOTHING_DOWNLOADED,
            drawThenPlay(draw = { throw Problem(MysteryProblem.NOTHING_DOWNLOADED) }, listeningTogether = { false }, play = {}),
        )
        assertEquals(
            MysteryProblem.FAILED,
            drawThenPlay(draw = { throw IOException("boom") }, listeningTogether = { false }, play = {}),
        )
    }
}
