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

    private var idleSince: Long? = null

    /**
     * How long to wait before the next poll: every second while the speaker plays or should
     * start, every 4 s for the first two minutes of a pause or a stop, then once a minute. Any
     * command from Sillon polls at once whatever this says.
     */
    fun nextPollMs(playWhenReady: Boolean): Long {
        if (playWhenReady || waiting) {
            idleSince = null
            return POLL_MS
        }
        val since = idleSince ?: now().also { idleSince = it }
        return if (now() - since < IDLE_BACKOFF_AFTER_MS) IDLE_POLL_MS else ASLEEP_POLL_MS
    }

    companion object {
        const val POLL_MS = 1_000L
        const val IDLE_POLL_MS = 4_000L
        const val ASLEEP_POLL_MS = 60_000L
        const val IDLE_BACKOFF_AFTER_MS = 120_000L
        const val MAX_FAILURES = 8
        const val FOREIGN_POLLS = 3
        const val START_TIMEOUT_MS = 20_000L
    }
}
