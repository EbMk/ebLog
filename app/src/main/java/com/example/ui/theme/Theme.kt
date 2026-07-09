package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = CleanBlueContainer,
    onPrimary = CleanBlueOnContainer,
    primaryContainer = CleanBlue,
    onPrimaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFE2E2E9),
    surface = Color.Black,
    onSurface = Color(0xFFE2E2E9),
    outline = CleanOutline,
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = CleanSurfaceVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CleanBlue,
    onPrimary = Color.White,
    primaryContainer = CleanBlueContainer,
    onPrimaryContainer = CleanBlueOnContainer,
    background = CleanBackground,
    onBackground = CleanOnBackground,
    surface = CleanSurface,
    onSurface = CleanOnSurface,
    outline = CleanOutline,
    surfaceVariant = CleanSurfaceVariant,
    onSurfaceVariant = CleanOnSurfaceVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
