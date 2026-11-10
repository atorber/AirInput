package app.airinput.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AirColors {
    val Ink = Color(0xFF0B1218)
    val InkLift = Color(0xFF121C24)
    val Panel = Color(0xFF18242F)
    val PanelLift = Color(0xFF213040)
    val Line = Color(0xFF2A3B4C)
    val Teal = Color(0xFF2EC4B6)
    val TealDim = Color(0xFF1A8F85)
    val Mist = Color(0xFFE7EEF3)
    val Mute = Color(0xFF8FA3B0)
    val Warn = Color(0xFFE3A45A)
    val Danger = Color(0xFFE57373)
    val Ok = Color(0xFF6BCB8B)
}

private val DarkColors = darkColorScheme(
    primary = AirColors.Teal,
    onPrimary = AirColors.Ink,
    primaryContainer = AirColors.TealDim,
    onPrimaryContainer = AirColors.Mist,
    secondary = AirColors.PanelLift,
    onSecondary = AirColors.Mist,
    background = AirColors.Ink,
    onBackground = AirColors.Mist,
    surface = AirColors.Panel,
    onSurface = AirColors.Mist,
    surfaceVariant = AirColors.PanelLift,
    onSurfaceVariant = AirColors.Mute,
    outline = AirColors.Line,
    error = AirColors.Danger,
    onError = AirColors.Ink,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFFDEE8EF),
    onSecondary = Color(0xFF12324A),
    background = Color(0xFFF3F7FA),
    onBackground = Color(0xFF142033),
    surface = Color.White,
    onSurface = Color(0xFF142033),
    surfaceVariant = Color(0xFFE8EEF3),
    onSurfaceVariant = Color(0xFF5B6B78),
    outline = Color(0xFFCDD8E1),
    error = Color(0xFFB42318),
    onError = Color.White,
)

private val AirTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = AirColors.Mute),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

private val AirShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirInputTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme || isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AirTypography,
        shapes = AirShapes,
        content = content,
    )
}
