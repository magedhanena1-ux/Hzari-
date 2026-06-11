package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Context

object ThemeConfig {
    val themeMode = mutableStateOf("system") // "system", "light", "dark"

    fun loadTheme(context: Context) {
        val prefs = context.getSharedPreferences("hathari_prefs", Context.MODE_PRIVATE)
        themeMode.value = prefs.getString("theme_mode", "system") ?: "system"
    }

    fun saveTheme(context: Context, mode: String) {
        themeMode.value = mode
        val prefs = context.getSharedPreferences("hathari_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", mode).apply()
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    background = Color(0xFF0F172A), // Slate-900 for dark mode
    surface = Color(0xFF1E293B),
    error = Danger,
    onSurface = Color(0xFFF8FAFC),
    onBackground = Color(0xFFF8FAFC)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = PrimaryForeground,
    background = Background,
    surface = CardColor,
    error = Danger,
    outline = BorderColor,
    onSurface = Color.Black,
    onBackground = Color.Black
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = when (ThemeConfig.themeMode.value) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = false, // Set to false to force our beautiful brand colors
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
