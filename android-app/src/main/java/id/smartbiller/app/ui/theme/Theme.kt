package id.smartbiller.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue900 = Color(0xFF061C33)
val Blue800 = Color(0xFF0B3A68)
val Blue600 = Color(0xFF1E88E5)
val Cyan = Color(0xFF38BDF8)
val Yellow = Color(0xFFFFD54F)

private val LightScheme = lightColorScheme(
    primary = Blue600,
    secondary = Cyan,
    tertiary = Yellow,
    background = Color(0xFFE9F3FB),
    surface = Color(0xFFF7FBFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    secondary = Cyan,
    tertiary = Yellow,
    background = Blue900,
    surface = Color(0xFF0D243A),
)

@Composable
fun SmartBillerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        content = content,
    )
}
