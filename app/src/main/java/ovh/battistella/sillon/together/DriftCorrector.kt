package ovh.battistella.sillon.together

import kotlin.math.abs

sealed interface Correction {
    data object None : Correction

    data class Speed(val rate: Float) : Correction

    data class Seek(val positionMs: Long) : Correction
}

data class GroupTimeline(
    val positionMs: Long,
    val serverTimeMs: Long,
    val playing: Boolean,
) {
    fun positionAt(serverNowMs: Long): Long =
        if (playing) positionMs + (serverNowMs - serverTimeMs).coerceAtLeast(0) else positionMs
}

object DriftCorrector {
    const val START_THRESHOLD_MS = 25L
    const val STOP_THRESHOLD_MS = 8L
    const val SEEK_THRESHOLD_MS = 600L

    const val CATCH_UP_WINDOW_MS = 2_000L
    const val MAX_RATE_DELTA = 0.05f

    fun drift(localPositionMs: Long, expectedPositionMs: Long): Long = localPositionMs - expectedPositionMs

    fun correct(driftMs: Long, expectedPositionMs: Long, correcting: Boolean): Correction {
        val gap = abs(driftMs)
        return when {
            gap >= SEEK_THRESHOLD_MS -> Correction.Seek(expectedPositionMs.coerceAtLeast(0))
            gap <= STOP_THRESHOLD_MS -> Correction.None
            gap < START_THRESHOLD_MS && !correcting -> Correction.None
            else -> {
                val delta = (driftMs.toFloat() / CATCH_UP_WINDOW_MS).coerceIn(-MAX_RATE_DELTA, MAX_RATE_DELTA)
                Correction.Speed(1f - delta)
            }
        }
    }
}
