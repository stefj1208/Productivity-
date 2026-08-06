package com.notresemaine.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.LocalTime

// Deux accents : un par personne. Paire lisible pour un daltonien,
// toujours accompagnée de l'initiale du prénom dans l'interface.
val AccentBlue = Color(0xFF5AA9FF)   // personne A
val AccentOrange = Color(0xFFFFB25C) // personne B
val NeutralGray = Color(0xFF9A9AA2)  // objectif non atteint : gris neutre, jamais rouge
val DeepBlack = Color(0xFF0A0A0C)

fun accentFor(colorRole: String): Color = if (colorRole == "B") AccentOrange else AccentBlue
fun accentLabel(colorRole: String): String = if (colorRole == "B") "Orange" else "Bleu"

private val DarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = DeepBlack,
    secondary = AccentOrange,
    onSecondary = DeepBlack,
    background = DeepBlack,
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF161619),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF222226),
    onSurfaceVariant = Color(0xFFC9C9CE),
    outline = Color(0xFF55555C),
    error = NeutralGray // pas de rouge d'alerte dans cette application
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFFB35C00),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1C),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1C),
    surfaceVariant = Color(0xFFECECEF),
    onSurfaceVariant = Color(0xFF44444A),
    outline = Color(0xFF75757C)
)

// Typographie système (Roboto), deux tailles : 32 sp pour la priorité, 16 sp pour le reste.
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp)
)

@Composable
fun AppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "clair" -> false
        "auto" -> {
            val h = LocalTime.now().hour
            h < 8 || h >= 20 || isSystemInDarkTheme()
        }
        else -> true // sombre par défaut
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
