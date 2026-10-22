package ovh.battistella.sillon.together

data class ClockSample(
    val sentAt: Long,
    val serverReceivedAt: Long,
    val serverSentAt: Long,
    val receivedAt: Long,
) {
    val offsetMs: Double get() = ((serverReceivedAt - sentAt) + (serverSentAt - receivedAt)) / 2.0

    val roundTripMs: Long get() = ((receivedAt - sentAt) - (serverSentAt - serverReceivedAt)).coerceAtLeast(0)
}

class ClockSync(private val window: Int = WINDOW) {
    private val samples = ArrayDeque<ClockSample>()

    @Synchronized
    fun add(sample: ClockSample) {
        if (sample.receivedAt < sample.sentAt || sample.serverSentAt < sample.serverReceivedAt) return
        samples.addLast(sample)
        while (samples.size > window) samples.removeFirst()
    }

    @Synchronized
    fun reset() = samples.clear()

    val sampleCount: Int @Synchronized get() = samples.size

    val isSynced: Boolean get() = sampleCount >= MIN_SAMPLES

    private val best: ClockSample? @Synchronized get() = samples.minByOrNull { it.roundTripMs }

    val offsetMs: Long get() = best?.offsetMs?.let { Math.round(it) } ?: 0L

    val roundTripMs: Long get() = best?.roundTripMs ?: 0L

    fun toServer(localMs: Long): Long = localMs + offsetMs

    fun toLocal(serverMs: Long): Long = serverMs - offsetMs

    companion object {
        const val WINDOW = 8
        const val MIN_SAMPLES = 3

        const val FAST_INTERVAL_MS = 1_000L
        const val SLOW_INTERVAL_MS = 60_000L
    }
}
