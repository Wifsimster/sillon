package ovh.battistella.sillon.desktop.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ovh.battistella.sillon.data.network.NetworkMonitor
import java.net.NetworkInterface

class DesktopNetworkMonitor : NetworkMonitor {
    override fun isMetered(): Boolean = false

    override fun isOnline(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence().any { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }
    }.getOrDefault(true)

    override val online: Flow<Boolean> = flow {
        while (true) {
            emit(isOnline())
            delay(POLL_MS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private companion object {
        const val POLL_MS = 5_000L
    }
}
