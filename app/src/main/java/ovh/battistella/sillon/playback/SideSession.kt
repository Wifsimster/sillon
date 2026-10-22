package ovh.battistella.sillon.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import ovh.battistella.sillon.data.plex.PlexJson
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sidesDataStore: DataStore<Preferences> by preferencesDataStore(name = "sides")

@Serializable
data class SavedSides(val plan: SidePlan, val ended: SideEnd? = null)

@Singleton
class SideSession @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val machine = SideMachine()

    private val _state = MutableStateFlow(SideState())
    val state = _state.asStateFlow()

    private val _planChanges = MutableStateFlow(0)
    val planChanges = _planChanges.asStateFlow()

    fun setPlan(plan: SidePlan?) {
        machine.setPlan(plan)
        _planChanges.value++
        publish()
    }

    fun publish() {
        _state.value = machine.state
    }

    suspend fun restore() {
        if (machine.plan != null) return
        val saved = context.sidesDataStore.data.first()[SIDES]?.let {
            runCatching { PlexJson.decodeFromString(SavedSides.serializer(), it) }.getOrNull()
        } ?: return
        machine.restore(saved.plan, saved.ended)
        _planChanges.value++
        publish()
    }

    suspend fun save() {
        val plan = machine.plan
        context.sidesDataStore.edit {
            if (plan == null) {
                it.remove(SIDES)
            } else {
                it[SIDES] = PlexJson.encodeToString(SavedSides.serializer(), SavedSides(plan, machine.ended))
            }
        }
    }

    companion object {
        const val ACTION_FLIP = "ovh.battistella.sillon.FLIP_SIDE"
        private val SIDES = stringPreferencesKey("sides")
    }
}
