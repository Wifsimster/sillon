package ovh.battistella.sillon.data.network

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    fun isMetered(): Boolean

    fun isOnline(): Boolean

    val online: Flow<Boolean>
}
