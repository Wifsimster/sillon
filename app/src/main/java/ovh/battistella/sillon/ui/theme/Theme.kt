package ovh.battistella.sillon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ovh.battistella.sillon.data.settings.ThemeAccent
import ovh.battistella.sillon.data.settings.ThemePreferences

object Brand {
    val Vinyl = Color(0xFF15120F)
    val Groove = Color(0xFF2A241E)
    val Sleeve = Color(0xFFF6EFE4)
    val Label = Color(0xFFE8A33D)
    val Rust = Color(0xFFC2552B)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFFA0401C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF3A0B00),
    tertiary = Color(0xFF5D5F3A),
    onTertiary = Color.White,
    background = Color(0xFFFBF6EE),
    onBackground = Brand.Vinyl,
    surface = Color(0xFFFBF6EE),
    onSurface = Brand.Vinyl,
    surfaceVariant = Color(0xFFEFE6D8),
    onSurfaceVariant = Color(0xFF5B5146),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F0E5),
    surfaceContainer = Color(0xFFF2EADD),
    surfaceContainerHigh = Color(0xFFECE3D5),
    surfaceContainerHighest = Color(0xFFE6DCCD),
    outline = Color(0xFF8C8174),
    outlineVariant = Color(0xFFD9CDBC),
    inverseSurface = Brand.Groove,
    inverseOnSurface = Brand.Sleeve,
    inversePrimary = Brand.Label,
)

private val DarkColors = darkColorScheme(
    primary = Brand.Label,
    onPrimary = Color(0xFF2C1600),
    primaryContainer = Color(0xFF6A3D00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFFFB59E),
    onSecondary = Color(0xFF5A1900),
    secondaryContainer = Color(0xFF7A2C0E),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFC6C89A),
    onTertiary = Color(0xFF2F3112),
    background = Brand.Vinyl,
    onBackground = Brand.Sleeve,
    surface = Brand.Vinyl,
    onSurface = Brand.Sleeve,
    surfaceVariant = Brand.Groove,
    onSurfaceVariant = Color(0xFFCFC4B5),
    surfaceContainerLowest = Color(0xFF100D0B),
    surfaceContainerLow = Color(0xFF1B1713),
    surfaceContainer = Color(0xFF1F1A16),
    surfaceContainerHigh = Color(0xFF29231E),
    surfaceContainerHighest = Color(0xFF342D27),
    outline = Color(0xFF988D7F),
    outlineVariant = Color(0xFF4A4037),
    inverseSurface = Brand.Sleeve,
    inverseOnSurface = Brand.Vinyl,
    inversePrimary = Color(0xFF8A5100),
)

private val SillonShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * The brand's scheme with [accent] in the primary role and its tint in the secondary container,
 * and black surfaces when [pureBlack] (dark only). Vinyl, sleeve and the rust secondary stay.
 */
fun sillonColorScheme(dark: Boolean, accent: ThemeAccent = ThemeAccent.AMBER, pureBlack: Boolean = false): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val tones = accent.tones(dark)
    val inverse = accent.tones(!dark)
    val accented = if (accent == ThemeAccent.AMBER) base else base.copy(
        primary = Color(tones.primary),
        onPrimary = Color(tones.onPrimary),
        primaryContainer = Color(tones.primaryContainer),
        onPrimaryContainer = Color(tones.onPrimaryContainer),
        inversePrimary = Color(inverse.primary),
        surfaceTint = Color(tones.primary),
        // The rust containers (progress tracks, the Listen radio cards) clash with other accents:
        // they take a soft tint of the accent instead. Rust itself stays the secondary colour.
        secondaryContainer = Color(tones.primaryContainer),
        onSecondaryContainer = Color(tones.onPrimaryContainer),
    )
    return if (dark && pureBlack) accented.blackened() else accented
}

/** Vinyl black becomes true black; the raised surfaces step down with it so cards still stand out. */
private fun ColorScheme.blackened(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0C0A),
    surfaceContainer = Color(0xFF141210),
    surfaceContainerHigh = Color(0xFF1E1B18),
    surfaceContainerHighest = Color(0xFF292521),
)

@Composable
fun SillonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    wallpaperColors: Boolean = false,
    accent: ThemeAccent = ThemeAccent.AMBER,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && pureBlack) dynamic.blackened() else dynamic
        }
        else -> sillonColorScheme(darkTheme, accent, pureBlack)
    }
    MaterialTheme(colorScheme = colorScheme, typography = SillonTypography, shapes = SillonShapes, content = content)
}

/** The app's theme as chosen on the theme page. */
@Composable
fun SillonTheme(preferences: ThemePreferences, content: @Composable () -> Unit) {
    SillonTheme(
        darkTheme = preferences.isDark(isSystemInDarkTheme()),
        wallpaperColors = preferences.wallpaperColors,
        accent = preferences.accent,
        pureBlack = preferences.pureBlack,
        content = content,
    )
}
