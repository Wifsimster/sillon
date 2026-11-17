package ovh.battistella.sillon.data.activity

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ovh.battistella.sillon.data.storage.AppStorage

@Singleton
class ActivityStore @Inject constructor(
    storage: AppStorage,
) {
    private val prefs = storage.preferences("activity")

    val notify: Flow<Boolean> = prefs.data.map { it[NOTIFY] ?: false }

    suspend fun setNotify(value: Boolean) {
        prefs.edit { it[NOTIFY] = value }
    }

    suspend fun ledger(): Map<String, Long> = ListenLedger.decode(prefs.data.first()[LEDGER])

    suspend fun saveLedger(ledger: Map<String, Long>) {
        prefs.edit { it[LEDGER] = ListenLedger.encode(ledger) }
    }

    private companion object {
        val NOTIFY = booleanPreferencesKey("notify")
        val LEDGER = stringPreferencesKey("ledger")
    }
}
