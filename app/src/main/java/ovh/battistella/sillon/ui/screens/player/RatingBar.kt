package ovh.battistella.sillon.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.rating.JellyfinStars
import ovh.battistella.sillon.data.rating.RatingSend
import ovh.battistella.sillon.data.rating.Stars

/** The rating of the track playing, as the player shows it. */
data class RatingUi(
    val key: String,
    val stars: Int?,
    val send: RatingSend? = null,
    val jellyfin: Boolean = false,
)

/** Five stars, always there: touch one to rate, touch it again to clear. */
@Composable
fun RatingBar(rating: RatingUi, onRate: (Int?) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val given = rating.stars?.let { pluralStringResource(R.plurals.rating_given, it, it) } ?: stringResource(R.string.rating_none)
    val clear = stringResource(R.string.rating_clear)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.semantics { stateDescription = given },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (n in 1..Stars.MAX) {
                val filled = rating.stars != null && n <= rating.stars
                val current = rating.stars == n
                val action = pluralStringResource(R.plurals.rating_give, n, n)
                // 1 star says "not for me": rust rather than the amber of a rating you like.
                val enabled = !rating.jellyfin || JellyfinStars.supports(n)
                val tint = when {
                    !filled && !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                    !filled -> MaterialTheme.colorScheme.onSurfaceVariant
                    rating.stars == 1 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
                IconButton(
                    onClick = { onRate(Stars.toggle(rating.stars, n)) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = if (current) clear else action },
                ) {
                    Icon(
                        if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
        when (rating.send) {
            RatingSend.WAITING_FOR_NETWORK -> Note(stringResource(R.string.rating_waiting))
            RatingSend.FAILED -> TextButton(onClick = onRetry) {
                Text(
                    stringResource(R.string.rating_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            else -> if (rating.jellyfin) Note(stringResource(R.string.rating_jellyfin_only))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private const val DISABLED_ALPHA = 0.38f
