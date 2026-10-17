package ovh.battistella.sillon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Warm amber accent on near-black, like a record label under a lamp.
private val Amber = Color(0xFF8A5100)
private val AmberLight = Color(0xFFFFB866)

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB9),
    onPrimaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFFF8F3),
    surface = Color(0xFFFFF8F3),
)

private val DarkColors = darkColorScheme(
    primary = AmberLight,
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDDB9),
    background = Color(0xFF17130F),
    surface = Color(0xFF17130F),
)

/** Material You on Android 12+, the amber scheme before that. */
@Composable
fun SillonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
