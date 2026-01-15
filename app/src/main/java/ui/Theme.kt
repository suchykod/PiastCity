package ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Prosta paleta kolorów dla ciemnego motywu
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4a99de), // Twój niebieski
    secondary = Color(0xFFfacc15), // Twój żółty
    background = Color(0xFF121212), // Ciemne tło
    surface = Color(0xFF1E1E1E), // Tło dla kart, paneli
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun PiastCityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Domyślnie używaj motywu systemowego
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme, // Na razie używamy tylko ciemnego motywu
        content = content
    )
}
    