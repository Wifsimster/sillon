package ovh.battistella.sillon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ovh.battistella.sillon.R

val Fraunces = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))

val DmSans = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_bold, FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.display() = copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold)
private fun TextStyle.text(weight: FontWeight = FontWeight.Normal) = copy(fontFamily = DmSans, fontWeight = weight)

val SillonTypography = Typography(
    displayLarge = base.displayLarge.display(),
    displayMedium = base.displayMedium.display(),
    displaySmall = base.displaySmall.display(),
    headlineLarge = base.headlineLarge.display(),
    headlineMedium = base.headlineMedium.display(),
    headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display().copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = base.titleMedium.text(FontWeight.Medium),
    titleSmall = base.titleSmall.text(FontWeight.Medium),
    bodyLarge = base.bodyLarge.text(),
    bodyMedium = base.bodyMedium.text(),
    bodySmall = base.bodySmall.text(),
    labelLarge = base.labelLarge.text(FontWeight.Medium),
    labelMedium = base.labelMedium.text(FontWeight.Medium),
    labelSmall = base.labelSmall.text(FontWeight.Medium),
)
