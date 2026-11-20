package ovh.battistella.sillon.data.settings

/** Light, dark, or whatever the system says. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The four colours of the primary role, as ARGB, for one side (light or dark) of an accent.
 * Kept free of Compose so the phone and the computer share the same values.
 */
data class AccentTones(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
)

/**
 * The accent colour of the app: buttons, switches, section titles, the progress bar.
 * [AMBER] is the label of the record, the brand's default. The light tones stay readable on
 * the cream background (WCAG AA), the dark tones on vinyl black.
 */
enum class ThemeAccent(val light: AccentTones, val dark: AccentTones) {
    AMBER(
        light = AccentTones(0xFF8A5100, 0xFFFFFFFF, 0xFFFFDDB3, 0xFF2C1600),
        dark = AccentTones(0xFFE8A33D, 0xFF2C1600, 0xFF6A3D00, 0xFFFFDDB3),
    ),
    RUST(
        light = AccentTones(0xFFA0401C, 0xFFFFFFFF, 0xFFFFDBCF, 0xFF3A0B00),
        dark = AccentTones(0xFFFFB59E, 0xFF5A1900, 0xFF7A2C0E, 0xFFFFDBCF),
    ),
    OLIVE(
        light = AccentTones(0xFF4E6628, 0xFFFFFFFF, 0xFFCFEBA6, 0xFF131F00),
        dark = AccentTones(0xFFB4CF8C, 0xFF233600, 0xFF384D1A, 0xFFCFEBA6),
    ),
    TEAL(
        light = AccentTones(0xFF006A66, 0xFFFFFFFF, 0xFF9EF0EB, 0xFF00201F),
        dark = AccentTones(0xFF82D3CF, 0xFF003735, 0xFF00504D, 0xFF9EF0EB),
    ),
    INDIGO(
        light = AccentTones(0xFF455E91, 0xFFFFFFFF, 0xFFD9E2FF, 0xFF001A43),
        dark = AccentTones(0xFFAFC6FF, 0xFF142F60, 0xFF2D4678, 0xFFD9E2FF),
    ),
    PLUM(
        light = AccentTones(0xFF7A4873, 0xFFFFFFFF, 0xFFFFD6F5, 0xFF30062C),
        dark = AccentTones(0xFFE9B3DE, 0xFF471A44, 0xFF60325B, 0xFFFFD6F5),
    ),
    ;

    fun tones(dark: Boolean): AccentTones = if (dark) this.dark else light
}

/** Everything the theme page chooses, read once by the app's theme. */
data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: ThemeAccent = ThemeAccent.AMBER,
    /** Black instead of vinyl black in dark: saves battery on OLED screens. */
    val pureBlack: Boolean = false,
    /** Material You, Android 12 and later. Overrides [accent]. */
    val wallpaperColors: Boolean = false,
) {
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
