package com.lala.assistant.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colores para el tema claro
private val LightColors = lightColors(
    primary = Color(0xFF6200EE),
    primaryVariant = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black
)

// Colores para el tema oscuro
private val DarkColors = darkColors(
    primary = Color(0xFFBB86FC),
    primaryVariant = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

/**
 * Tema principal de la aplicación
 */
@Composable
fun LalaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

/**
 * Gestor del tema de la aplicación
 */
object ThemeManager {
    // Preferencia de tema (0: sistema, 1: claro, 2: oscuro)
    private var themeMode = 0
    
    /**
     * Inicializa el gestor de tema con las preferencias guardadas
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("lala_prefs", Context.MODE_PRIVATE)
        themeMode = prefs.getInt("theme_mode", 0)
    }
    
    /**
     * Cambia el modo de tema
     */
    fun setThemeMode(context: Context, mode: Int) {
        if (mode in 0..2) {
            themeMode = mode
            
            // Guardar preferencia
            val prefs = context.getSharedPreferences("lala_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("theme_mode", mode).apply()
        }
    }
    
    /**
     * Obtiene si se debe usar tema oscuro
     */
    fun shouldUseDarkTheme(isSystemDark: Boolean): Boolean {
        return when (themeMode) {
            0 -> isSystemDark     // Seguir sistema
            1 -> false            // Siempre claro
            2 -> true             // Siempre oscuro
            else -> isSystemDark
        }
    }
}