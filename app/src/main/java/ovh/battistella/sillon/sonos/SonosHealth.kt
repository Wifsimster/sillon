package ovh.battistella.sillon.sonos

/**
 * When to give a Sonos session up, from the polls and from what Sillon itself asked: the speaker
 * stops answering, never starts after a play, or plays something that isn't Sillon's.
 */
class SonosHealth(private val now: () -> Long) {
    private var failures = 0
    private var foreign = 0
    private var waitingSince: Long? = null

    /** Waiting for the speaker to start after a play. */
    val waiting: Boolean get() = waitingSince != null

    /** Sillon asked the speaker to play: it has [START_TIMEOUT_MS] to start. */
    fun playAsked() {
        if (waitingSince == null) waitingSince = now()
    }

    /** Sillon paused or stopped the speaker: nothing to wait for. */
    fun pauseAsked() {
        waitingSince = null
    }

    fun pollFailed(): SonosLoss? = if (++failures >= MAX_FAILURES) SonosLoss.UNREACHABLE else null

    fun polled(status: SonosStatus, ours: Boolean): SonosLoss? {
        failures = 0
        // Started, or paused from elsewhere (the Sonos app): nothing to wait for any more.
        if (status.isPlaying || status.transport == Transport.PAUSED) waitingSince = null
        foreign = if (ours) 0 else foreign + 1
        return when {
            foreign >= FOREIGN_POLLS -> SonosLoss.TAKEN_OVER
            waitingSince?.let { now() - it > START_TIMEOUT_MS } == true -> SonosLoss.COULD_NOT_PLAY
            else -> null
        }
    }

    companion object {
        const val MAX_FAILURES = 8
        const val FOREIGN_POLLS = 3
        const val START_TIMEOUT_MS = 20_000L
    }
}
