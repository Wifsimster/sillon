package ovh.battistella.sillon.desktop.player

import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.desktop.ui.player.Waveform

class Waveforms(private val repository: PlexRepository) {
    private val cache = object : LinkedHashMap<String, List<Float>>(CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Float>>?): Boolean = size > CACHED
    }

    suspend fun of(trackKey: String): List<Float> {
        synchronized(cache) { cache[trackKey] }?.let { return it }
        val heights = runCatching { Waveform.heights(repository.loudnessLevels(trackKey, Waveform.SAMPLES)) }.getOrDefault(emptyList())
        if (heights.isNotEmpty()) synchronized(cache) { cache[trackKey] = heights }
        return heights
    }

    private companion object {
        const val CACHED = 64
    }
}
