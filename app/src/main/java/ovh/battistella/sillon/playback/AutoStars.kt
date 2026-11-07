package ovh.battistella.sillon.playback

import ovh.battistella.sillon.data.rating.Stars

object AutoStars {
    private val STEPS = listOf(3, 4, 2)

    fun offered(jellyfin: Boolean): Boolean = !jellyfin

    fun next(current: Int?): Int? = when (current) {
        null, 1, Stars.MAX -> STEPS.first()
        STEPS.last() -> null
        else -> STEPS[STEPS.indexOf(current) + 1]
    }

    fun shown(current: Int?): Int? = current?.takeIf { it in 2..4 }
}
