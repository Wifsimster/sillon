package ovh.battistella.sillon.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.genres.Genre
import ovh.battistella.sillon.data.genres.Genres
import ovh.battistella.sillon.ui.components.SillonMark
import ovh.battistella.sillon.ui.theme.Brand
import ovh.battistella.sillon.ui.theme.Fraunces

/** A genre as a tile: its spelling, its album count and a colour drawn from its name. */
data class GenreTileUi(
    val key: String,
    val label: String,
    val albums: Int,
    val color: Long = Genres.colorOf(key),
)

fun Genre.toTile() = GenreTileUi(key, label, albums.size)

/** Genre tiles two by two; they sit inside the search page's list, so they are laid out in plain rows. */
@Composable
fun GenreGrid(genres: List<GenreTileUi>, onGenre: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        genres.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { GenreTile(it, onGenre, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GenreTile(genre: GenreTileUi, onGenre: (String) -> Unit, modifier: Modifier = Modifier) {
    val count = pluralStringResource(R.plurals.search_genre_albums, genre.albums, genre.albums)
    val description = stringResource(R.string.search_genre_tile, genre.label, count)
    val openLabel = stringResource(R.string.search_genre_open)
    Box(
        modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(genre.color))
            .clickable(onClickLabel = openLabel) { onGenre(genre.key) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(openLabel) { onGenre(genre.key); true }
            },
    ) {
        // The groove, as on the sleeve: decorative, half outside the tile.
        SillonMark(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 18.dp, y = 18.dp).size(64.dp),
            groove = Brand.Sleeve.copy(alpha = 0.16f),
            label = Brand.Label.copy(alpha = 0.55f),
        )
        Column(Modifier.padding(start = 14.dp, end = 40.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                genre.label,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
                color = Brand.Sleeve,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                count,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Sleeve.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
