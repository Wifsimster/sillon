package ovh.battistella.sillon.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidStorage @Inject constructor(
    @ApplicationContext context: Context,
) : AppStorage {
    private val context = context.applicationContext

    override fun preferences(name: String): DataStore<Preferences> = stores.getOrPut(name) {
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(name) }
    }

    override val privateDir: File get() = context.noBackupFilesDir

    override val filesDir: File get() = context.filesDir

    private companion object {
        val stores = ConcurrentHashMap<String, DataStore<Preferences>>()
    }
}
