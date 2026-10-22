package ovh.battistella.sillon.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftCorrectorTest {
    @Test
    fun `the group timeline moves only while playing`() {
        val playing = GroupTimeline(positionMs = 30_000, serverTimeMs = 1_000, playing = true)
        assertEquals(32_500L, playing.positionAt(3_500))
        assertEquals(30_000L, playing.positionAt(500))

        val paused = playing.copy(playing = false)
        assertEquals(30_000L, paused.positionAt(60_000))
    }

    @Test
    fun `drift is positive when this phone is ahead`() {
        assertEquals(40L, DriftCorrector.drift(localPositionMs = 10_040, expectedPositionMs = 10_000))
        assertEquals(-25L, DriftCorrector.drift(localPositionMs = 9_975, expectedPositionMs = 10_000))
    }

    @Test
    fun `a small gap is left alone`() {
        assertEquals(Correction.None, DriftCorrector.correct(15, 10_000, correcting = false))
        assertEquals(Correction.None, DriftCorrector.correct(-20, 10_000, correcting = false))
    }

    @Test
    fun `a phone ahead slows down, one behind speeds up`() {
        val ahead = DriftCorrector.correct(60, 10_000, correcting = false) as Correction.Speed
        assertEquals(1f - 60f / DriftCorrector.CATCH_UP_WINDOW_MS, ahead.rate, 1e-6f)
        assertTrue(ahead.rate < 1f)

        val behind = DriftCorrector.correct(-60, 10_000, correcting = false) as Correction.Speed
        assertTrue(behind.rate > 1f)
    }

    @Test
    fun `the speed change stays inaudible`() {
        val ahead = DriftCorrector.correct(500, 10_000, correcting = false) as Correction.Speed
        assertEquals(1f - DriftCorrector.MAX_RATE_DELTA, ahead.rate, 1e-6f)
        val behind = DriftCorrector.correct(-500, 10_000, correcting = false) as Correction.Speed
        assertEquals(1f + DriftCorrector.MAX_RATE_DELTA, behind.rate, 1e-6f)
    }

    @Test
    fun `hysteresis keeps correcting until the gap is really closed`() {
        assertEquals(Correction.None, DriftCorrector.correct(15, 10_000, correcting = false))
        assertTrue(DriftCorrector.correct(15, 10_000, correcting = true) is Correction.Speed)
        assertEquals(Correction.None, DriftCorrector.correct(5, 10_000, correcting = true))
    }

    @Test
    fun `a large gap is closed by a seek to the group`() {
        assertEquals(Correction.Seek(42_000), DriftCorrector.correct(-900, 42_000, correcting = false))
        assertEquals(Correction.Seek(0), DriftCorrector.correct(800, -50, correcting = true))
    }

    @Test
    fun `a gap within the target is reached within a few seconds`() {
        var drift = -100.0
        var correcting = false
        var elapsed = 0
        while (elapsed < 10_000) {
            val correction = DriftCorrector.correct(Math.round(drift), 0, correcting)
            val rate = (correction as? Correction.Speed)?.rate ?: 1f
            correcting = correction is Correction.Speed
            drift += (rate - 1.0) * 250
            elapsed += 250
            if (!correcting && kotlin.math.abs(drift) <= DriftCorrector.STOP_THRESHOLD_MS) break
        }
        assertTrue("drift $drift after $elapsed ms", kotlin.math.abs(drift) <= DriftCorrector.STOP_THRESHOLD_MS)
        assertTrue("took $elapsed ms", elapsed <= 5_000)
    }
}
