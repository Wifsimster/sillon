package ovh.battistella.sillon.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import java.io.File

internal interface MpvLibrary : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property(handle: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_observe_property(handle: Pointer, userdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(handle: Pointer)
    fun mpv_error_string(error: Int): String
}

internal interface CRuntime : Library {
    fun setlocale(category: Int, locale: String): Pointer?
}

@Structure.FieldOrder("eventId", "error", "replyUserdata", "data")
internal class MpvEvent(pointer: Pointer) : Structure(pointer) {
    @JvmField var eventId: Int = 0
    @JvmField var error: Int = 0
    @JvmField var replyUserdata: Long = 0
    @JvmField var data: Pointer? = null

    init {
        read()
    }
}

@Structure.FieldOrder("name", "format", "data")
internal class MpvEventProperty(pointer: Pointer) : Structure(pointer) {
    @JvmField var name: String? = null
    @JvmField var format: Int = 0
    @JvmField var data: Pointer? = null

    init {
        read()
    }
}

@Structure.FieldOrder("entryId")
internal class MpvEventStartFile(pointer: Pointer) : Structure(pointer) {
    @JvmField var entryId: Long = 0

    init {
        read()
    }
}

@Structure.FieldOrder("reason", "error", "entryId")
internal class MpvEventEndFile(pointer: Pointer) : Structure(pointer) {
    @JvmField var reason: Int = 0
    @JvmField var error: Int = 0
    @JvmField var entryId: Long = 0

    init {
        read()
    }
}

enum class EndReason { FINISHED, FAILED, OTHER }

sealed interface MpvSignal {
    data object Wakeup : MpvSignal
    data object Shutdown : MpvSignal
    data class FileStarted(val entryId: Long?) : MpvSignal
    data object FileLoaded : MpvSignal
    data class FileEnded(val entryId: Long?, val reason: EndReason) : MpvSignal
    data class PropertyChanged(val name: String) : MpvSignal
}

class MpvException(message: String) : RuntimeException(message)

class Mpv private constructor(private val lib: MpvLibrary, private val handle: Pointer) {
    fun option(name: String, value: String): Boolean = lib.mpv_set_option_string(handle, name, value) >= 0

    fun initialize() {
        val code = lib.mpv_initialize(handle)
        if (code < 0) throw MpvException("mpv_initialize: ${lib.mpv_error_string(code)}")
    }

    fun command(vararg args: String): Boolean {
        val array = arrayOfNulls<String>(args.size + 1)
        args.forEachIndexed { i, arg -> array[i] = arg }
        return lib.mpv_command(handle, array) >= 0
    }

    fun set(name: String, value: String): Boolean = lib.mpv_set_property_string(handle, name, value) >= 0

    fun flag(name: String): Boolean? {
        val ref = IntByReference()
        return if (lib.mpv_get_property(handle, name, FORMAT_FLAG, ref.pointer) >= 0) ref.value != 0 else null
    }

    fun long(name: String): Long? {
        val ref = LongByReference()
        return if (lib.mpv_get_property(handle, name, FORMAT_INT64, ref.pointer) >= 0) ref.value else null
    }

    fun double(name: String): Double? {
        val ref = DoubleByReference()
        return if (lib.mpv_get_property(handle, name, FORMAT_DOUBLE, ref.pointer) >= 0) ref.value else null
    }

    fun observe(name: String) {
        lib.mpv_observe_property(handle, 0, name, FORMAT_NONE)
    }

    fun await(timeoutSeconds: Double): MpvSignal? {
        val event = MpvEvent(lib.mpv_wait_event(handle, timeoutSeconds))
        return when (event.eventId) {
            EVENT_NONE -> null
            EVENT_SHUTDOWN -> MpvSignal.Shutdown
            EVENT_START_FILE -> MpvSignal.FileStarted(event.data?.let(::MpvEventStartFile)?.entryId)
            EVENT_FILE_LOADED -> MpvSignal.FileLoaded
            EVENT_END_FILE -> {
                val end = event.data?.let(::MpvEventEndFile)
                MpvSignal.FileEnded(
                    end?.entryId,
                    when (end?.reason) {
                        END_FILE_REASON_EOF -> EndReason.FINISHED
                        END_FILE_REASON_ERROR -> EndReason.FAILED
                        else -> EndReason.OTHER
                    },
                )
            }
            EVENT_PROPERTY_CHANGE -> event.data?.let(::MpvEventProperty)?.name?.let(MpvSignal::PropertyChanged) ?: MpvSignal.Wakeup
            else -> MpvSignal.Wakeup
        }
    }

    fun wakeup() = lib.mpv_wakeup(handle)

    fun destroy() = lib.mpv_terminate_destroy(handle)

    companion object {
        private const val FORMAT_NONE = 0
        private const val FORMAT_FLAG = 3
        private const val FORMAT_INT64 = 4
        private const val FORMAT_DOUBLE = 5
        private const val EVENT_NONE = 0
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_START_FILE = 6
        private const val EVENT_END_FILE = 7
        private const val EVENT_FILE_LOADED = 8
        private const val EVENT_PROPERTY_CHANGE = 22
        private const val END_FILE_REASON_EOF = 0
        private const val END_FILE_REASON_ERROR = 4

        private val names = listOf("libmpv-2", "mpv-2", "mpv")

        fun create(searchDirs: List<File> = emptyList()): Mpv {
            val lib = load(searchDirs)
            useCNumericLocale()
            val handle = lib.mpv_create() ?: throw MpvException("mpv_create failed")
            return Mpv(lib, handle)
        }

        fun isAvailable(searchDirs: List<File> = emptyList()): Boolean = runCatching { load(searchDirs) }.isSuccess

        private fun useCNumericLocale() {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val (runtimes, category) = when {
                os.contains("win") -> listOf("ucrtbase", "msvcrt") to 4
                os.contains("mac") -> listOf("c") to 4
                else -> listOf("c") to 1
            }
            for (runtime in runtimes) {
                runCatching { Native.load(runtime, CRuntime::class.java).setlocale(category, "C") }
            }
        }

        private fun load(searchDirs: List<File>): MpvLibrary {
            searchDirs.filter { it.isDirectory }.forEach { dir ->
                names.forEach { name -> com.sun.jna.NativeLibrary.addSearchPath(name, dir.absolutePath) }
            }
            var last: Throwable? = null
            for (name in names) {
                try {
                    return Native.load(name, MpvLibrary::class.java)
                } catch (e: UnsatisfiedLinkError) {
                    last = e
                }
            }
            throw MpvException("libmpv not found: ${last?.message}")
        }
    }
}
