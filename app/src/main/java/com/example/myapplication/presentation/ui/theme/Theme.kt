package com.example.myapplication.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ComfyUI GO 主题色彩
private val ComfyUIBlue = Color(0xFF3498DB)
private val ComfyUIGreen = Color(0xFF2ECC71)
private val ComfyUIDarkBlue = Color(0xFF2980B9)
private val ComfyUILightGray = Color(0xFFF8F9FA)
private val ComfyUIDarkGray = Color(0xFF2C3E50)

private val DarkColorScheme = darkColorScheme(
    primary = ComfyUIBlue,
    secondary = ComfyUIGreen,
    tertiary = ComfyUIDarkBlue,
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF2D2D2D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = ComfyUIBlue,
    secondary = ComfyUIGreen,
    tertiary = ComfyUIDarkBlue,
    background = ComfyUILightGray,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ComfyUIDarkGray,
    onSurface = ComfyUIDarkGray,
)

@Composable
fun ComfyUIGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
