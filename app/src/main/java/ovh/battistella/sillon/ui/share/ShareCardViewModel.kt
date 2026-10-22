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
        _ui.value = ShareCardUi(source = source, detail = detail(source, year = source.year))
        viewModelScope.launch(Dispatchers.IO) { CardFiles.purge(context) }
        viewModelScope.launch {
            val coverJob = async { loadCover(source.artworkUri) }
            val enriched = enrich(source)
            cover = coverJob.await()
            palette = withContext(Dispatchers.Default) { CardPalette.of(cover) }
            _ui.update { enriched(it).copy(loading = false) }
        }
    }

    /**
     * Year, rating and lyrics come from the server when it answers. Offline (a downloaded album),
     * the card keeps what the screen already knew: title, artist and cover.
     */
    private suspend fun enrich(source: ShareSource): (ShareCardUi) -> ShareCardUi {
        val isJellyfin = runCatching { repository.isJellyfin.first() }.getOrDefault(false)
        val key = source.key ?: return { it.copy(isJellyfin = isJellyfin) }
        val meta = runCatching { repository.metadata(key) }.getOrNull()
        val lyrics = if (source.kind == ShareSource.Kind.TRACK) {
            runCatching { repository.lyrics(key) }.getOrDefault(emptyList())
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
        return try {
            val card = draw(state, text, scale = 1f)
            val file = withContext(Dispatchers.IO) { CardFiles.write(context, card) }
            card.recycle()
            CardFiles.uri(context, file)
        } catch (e: java.io.IOException) {
            null
        } finally {
            _ui.update { it.copy(exporting = false) }
        }
    }

    private companion object {
        const val COVER_PX = 1080
        const val PREVIEW_SCALE = 0.5f
        const val PREVIEW_DEBOUNCE_MS = 120L
    }
}
