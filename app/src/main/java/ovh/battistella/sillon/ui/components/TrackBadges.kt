package ovh.battistella.sillon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.R
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun PopularityBars(popularity: Float, modifier: Modifier = Modifier) {
    val lit = ceil(popularity.coerceIn(0f, 1f) * BARS).toInt().coerceAtLeast(1)
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val label = stringResource(R.string.track_popularity, lit, BARS)
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(BARS) { i ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((4 + i * 2).dp)
                    .background(if (i < lit) on else off, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
fun RatingStars(stars: Float, modifier: Modifier = Modifier) {
    val full = floor(stars).toInt().coerceIn(0, 5)
    val half = stars - full >= 0.5f
    val tint = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.track_rating, stars)
    Row(modifier.semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        repeat(full) { Icon(Icons.Rounded.Star, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp)) }
        if (half) {
            Icon(Icons.Rounded.StarHalf, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        }
    }
}

private const val BARS = 5
