package de.fgna.pocketdev.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF006E78)
private val AccentDark = Color(0xFF75D7E2)
private val Success = Color(0xFF4D8B68)
private val SuccessDark = Color(0xFF73B38F)
private val BackgroundLight = Color(0xFFF8F5F0)
private val SurfaceLight = Color(0xFFFCFAF6)
private val InkLight = Color(0xFF2E2B27)
private val MutedLight = Color(0xFF746D64)
private val HairlineLight = Color(0xFFDDD7CE)
private val BackgroundDark = Color(0xFF11110F)
private val SurfaceDark = Color(0xFF151512)
private val InkDark = Color(0xFFF0ECE5)
private val MutedDark = Color(0xFF99958E)
private val HairlineDark = Color(0xFF2B2A26)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    tertiary = Success,
    background = BackgroundLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = MutedLight,
    outline = HairlineLight,
    outlineVariant = HairlineLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = BackgroundDark,
    tertiary = SuccessDark,
    background = BackgroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = MutedDark,
    outline = HairlineDark,
    outlineVariant = HairlineDark,
)

private val PocketDevTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun PocketDevTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = PocketDevTypography,
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
