package ovh.battistella.sillon.data.activity

data class LedgerUpdate(val started: List<ActiveListen>, val ledger: Map<String, Long>)

object ListenLedger {
    const val QUIET_MS = 30 * 60 * 1000L

    fun update(
        ledger: Map<String, Long>,
        serverId: String,
        listens: List<ActiveListen>,
        nowMs: Long,
        quietMs: Long = QUIET_MS,
    ): LedgerUpdate {
        val fresh = ledger.filterValues { nowMs - it < quietMs }
        val heard = listens.filter { !it.mine && it.state != ListenState.PAUSED }.distinctBy { it.listenerKey }
        val started = heard.filter { key(serverId, it) !in fresh }
        return LedgerUpdate(started, fresh + heard.associate { key(serverId, it) to nowMs })
    }

    fun encode(ledger: Map<String, Long>): String =
        ledger.entries.joinToString("\n") { (key, at) -> "$at $key" }

    fun decode(text: String?): Map<String, Long> =
        text.orEmpty().lineSequence().mapNotNull { line ->
            val at = line.substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
            val key = line.substringAfter(' ', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to at
        }.toMap()

    private fun key(serverId: String, listen: ActiveListen) = "$serverId|${listen.listenerKey}"
}
