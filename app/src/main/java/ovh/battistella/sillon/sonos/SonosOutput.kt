package ovh.battistella.sillon.sonos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** Where the music plays. */
sealed interface Output {
    data object Phone : Output

    data class Connecting(val group: SonosGroup) : Output

    data class OnSonos(val group: SonosGroup) : Output
}

/** Something to tell the user once, after the output changed on its own or refused to. */
enum class OutputNotice {
    UNREACHABLE,
    COULD_NOT_PLAY,
    TAKEN_OVER,
    FAILED,
    MYSTERY_ON_PHONE,
    TOGETHER_ON_PHONE,
}

sealed interface OutputRequest {
    data class PlayOn(val group: SonosGroup) : OutputRequest

    data object BackToPhone : OutputRequest
}

/**
 * The output picker's state: the Sonos groups found on the Wi-Fi, where the music plays, and the
 * requests the playback service carries out.
 */
@Singleton
class SonosOutput @Inject constructor(
    @ApplicationContext context: Context,
    http: OkHttpClient,
) {
    val client = SonosClient(http)
    private val discovery = SonosDiscovery(context, client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scan: Job? = null

    private val _groups = MutableStateFlow<List<SonosGroup>>(emptyList())
    val groups = _groups.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    private val _output = MutableStateFlow<Output>(Output.Phone)
    val output = _output.asStateFlow()

    private val _notice = MutableStateFlow<OutputNotice?>(null)
    val notice = _notice.asStateFlow()

    private val _requests = Channel<OutputRequest>(Channel.CONFLATED)
    val requests: Flow<OutputRequest> = _requests.receiveAsFlow()

    fun scan() {
        if (scan?.isActive == true) return
        scan = scope.launch {
            _scanning.value = true
            _groups.value = runCatching { discovery.discover() }.getOrDefault(emptyList())
            _scanning.value = false
        }
    }

    fun playOn(group: SonosGroup) {
        _notice.value = null
        _requests.trySend(OutputRequest.PlayOn(group))
    }

    fun backToPhone() {
        _notice.value = null
        _requests.trySend(OutputRequest.BackToPhone)
    }

    fun noticeSeen() {
        _notice.value = null
    }

    internal fun set(output: Output) {
        _output.value = output
    }

    internal fun tell(notice: OutputNotice) {
        _notice.value = notice
    }
}
