package ovh.battistella.sillon.desktop.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import ovh.battistella.sillon.data.storage.AppStorage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DesktopStorage(
    private val root: File = defaultRoot(),
    private val localRoot: File = defaultLocalRoot(),
) : AppStorage {
    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    override fun preferences(name: String): DataStore<Preferences> = stores.getOrPut(name) {
        PreferenceDataStoreFactory.create { File(root, "datastore/$name.preferences_pb").also { it.parentFile.mkdirs() } }
    }

    override val privateDir: File get() = File(localRoot, "cache").also { it.mkdirs() }

    override val filesDir: File get() = File(localRoot, "files").also { it.mkdirs() }

    companion object {
        private const val APP = "Sillon"

        private val os = System.getProperty("os.name").orEmpty().lowercase()
        private val home = File(System.getProperty("user.home"))

        fun defaultRoot(): File = System.getProperty("sillon.data.dir")?.let(::File) ?: when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path, APP)
            os.contains("mac") -> File(home, "Library/Application Support/$APP")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: File(home, ".config").path, APP.lowercase())
        }

        fun defaultLocalRoot(): File = System.getProperty("sillon.data.dir")?.let { File(it, "local") } ?: when {
            os.contains("win") -> File(System.getenv("LOCALAPPDATA") ?: File(home, "AppData/Local").path, APP)
            os.contains("mac") -> File(home, "Library/Caches/$APP")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, APP.lowercase())
        }
    }
}
