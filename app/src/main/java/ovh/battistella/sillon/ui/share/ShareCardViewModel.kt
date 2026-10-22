package ovh.battistella.sillon.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ovh.battistella.sillon.data.repository.PlexRepository
import javax.inject.Inject

data class ShareCardUi(
    val source: ShareSource? = null,
    val format: CardFormat = CardFormat.SQUARE,
    val template: CardTemplate = CardTemplate.VINYL,
    val note: String = "",
    val stars: Float? = null,
    val showStars: Boolean = false,
    val detail: String? = null,
    val isJellyfin: Boolean = false,
    val lyrics: List<String> = emptyList(),
    val chosenLyrics: List<Int> = emptyList(),
    val preview: Bitmap? = null,
    val loading: Boolean = true,
    val exporting: Boolean = false,
) {
    /** What goes on the card, and nothing else. */
    fun text(quote: (String) -> String): CardText? {
        val s = source ?: return null
        return CardText(
            title = s.title,
            artist = s.artist,
            detail = detail,
            stars = if (showStars) CardRules.stars(stars) else null,
            note = CardRules.note(note)?.let(quote),
            lyrics = chosenLyrics.mapNotNull { lyrics.getOrNull(it) },
        )
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ShareCardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlexRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ShareCardUi())
    val ui = _ui.asStateFlow()

    private val renderer by lazy { CardRenderer(context) }
    private var cover: Bitmap? = null
    private var palette = CardPalette.Default
    private var quote: (String) -> String = { "“$it”" }
    private val renderLock = Mutex()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            _ui.map { it.copy(preview = null, exporting = false) }
                .distinctUntilChanged()
                .debounce(PREVIEW_DEBOUNCE_MS)
                .collectLatest { state ->
                    if (state.loading) return@collectLatest
                    val text = state.text(quote) ?: return@collectLatest
                    val bitmap = draw(state, text, PREVIEW_SCALE)
                    _ui.update { it.copy(preview = bitmap) }
                }
        }
    }

    /** One render at a time: the renderer reuses its paint. */
    private suspend fun draw(state: ShareCardUi, text: CardText, scale: Float): Bitmap = renderLock.withLock {
        withContext(Dispatchers.Default) {
            renderer.render(renderer.layout(state.format, state.template, text), cover, palette, scale)
        }
    }

    fun open(source: ShareSource, quote: (String) -> String) {
        if (_ui.value.source == source) return
        this.quote = quote
        // A card still loading for another track must not land on this one.
        loadJob?.cancel()
        _ui.value = ShareCardUi(source = source, detail = detail(source, year = source.year))
        viewModelScope.launch(Dispatchers.IO) { CardFiles.purge(context) }
        loadJob = viewModelScope.launch {
            val coverJob = async { withTimeoutOrNull(LOAD_TIMEOUT_MS) { attempt { loadCover(source.artworkUri) } } }
            // A server that doesn't answer leaves the card with what the screen knew, not a grey Share button.
            val enriched = withTimeoutOrNull(LOAD_TIMEOUT_MS) { enrich(source) }
            val art = coverJob.await()
            val colours = art?.let { withContext(Dispatchers.Default) { CardPalette.of(it) } } ?: CardPalette.Default
            if (_ui.value.source != source) return@launch
            cover = art
            palette = colours
            _ui.update { (enriched?.invoke(it) ?: it).copy(loading = false) }
        }
    }

    /**
     * Year, rating and lyrics come from the server when it answers. Offline (a downloaded album),
     * the card keeps what the screen already knew: title, artist and cover.
     */
    private suspend fun enrich(source: ShareSource): (ShareCardUi) -> ShareCardUi {
        val isJellyfin = attempt { repository.isJellyfin.first() } ?: false
        val key = source.key ?: return { it.copy(isJellyfin = isJellyfin) }
        val meta = attempt { repository.metadata(key) }
        val lyrics = if (source.kind == ShareSource.Kind.TRACK) {
            attempt { repository.lyrics(key) }.orEmpty()
        } else {
            emptyList()
        }
        val year = source.year ?: (meta?.parentYear ?: meta?.year)?.takeIf { it > 0 }?.toString()
        val stars = CardRules.stars(meta?.stars)
        return { ui ->
            ui.copy(
                isJellyfin = isJellyfin,
                stars = stars,
                showStars = stars != null,
                detail = detail(source, year),
                lyrics = lyrics,
            )
        }
    }

    /** Null when the server fails; cancellation still cancels. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun detail(source: ShareSource, year: String?): String? = when (source.kind) {
        ShareSource.Kind.TRACK -> listOfNotNull(source.album?.takeIf { it.isNotBlank() }, year).joinToString(" · ")
        ShareSource.Kind.ALBUM, ShareSource.Kind.ARTIST -> year
    }?.takeIf { it.isNotBlank() }

    private suspend fun loadCover(uri: String?): Bitmap? {
        if (uri.isNullOrBlank()) return null
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(COVER_PX)
            .allowHardware(false)
            .build()
        return (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }

    fun setFormat(format: CardFormat) = _ui.update { it.copy(format = format) }
    fun setTemplate(template: CardTemplate) = _ui.update { it.copy(template = template) }
    fun setNote(note: String) = _ui.update { it.copy(note = CardRules.limit(note)) }
    fun setShowStars(show: Boolean) = _ui.update { it.copy(showStars = show && it.stars != null) }
    fun toggleLyric(index: Int) = _ui.update { it.copy(chosenLyrics = CardRules.toggleLyric(it.chosenLyrics, index)) }

    /** Renders the full-size card into the cache and returns its content:// URI for the share sheet. */
    suspend fun export(): Uri? {
        val state = _ui.value
        val text = state.text(quote) ?: return null
        _ui.update { it.copy(exporting = true) }
        var card: Bitmap? = null
        return try {
            val drawn = draw(state, text, scale = 1f).also { card = it }
            val file = withContext(Dispatchers.IO) { CardFiles.write(context, drawn) } ?: return null
            CardFiles.uri(context, file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            card?.recycle()
            _ui.update { it.copy(exporting = false) }
        }
    }

    private companion object {
        const val COVER_PX = 1080
        const val PREVIEW_SCALE = 0.5f
        const val PREVIEW_DEBOUNCE_MS = 120L
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
