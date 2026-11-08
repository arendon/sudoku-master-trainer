package com.arendon.sudokumaster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IndigoPrimary = Color(0xFF1E3A8A)
val IndigoSecondary = Color(0xFF2563EB)
val TealAccent = Color(0xFF0D9488)
val EmeraldSuccess = Color(0xFF059669)
val AmberWarning = Color(0xFFD97706)
val CrimsonElimination = Color(0xFFDC2626)
val SlateBackground = Color(0xFFF8FAFC)
val SurfaceCard = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = TealAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = AmberWarning,
    background = SlateBackground,
    surface = SurfaceCard,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun SudokuMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
