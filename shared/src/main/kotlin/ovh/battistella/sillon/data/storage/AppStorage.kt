package ovh.battistella.sillon.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

interface AppStorage {
    fun preferences(name: String): DataStore<Preferences>

    val privateDir: File

    val filesDir: File
}
