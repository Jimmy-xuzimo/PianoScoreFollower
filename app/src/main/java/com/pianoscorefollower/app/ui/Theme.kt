package com.pianoscorefollower.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** How the app decides between the light and the dark scheme. */
enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/**
 * Accent families the player can pick from.
 *
 * [Dynamic] hands the choice to the device: on Android 12 and up Material You derives
 * a full scheme from the wallpaper, which is the native behaviour. The rest are fixed
 * hues run through the same tonal generator, so every palette stays readable in both
 * light and dark.
 */
enum class ThemePalette(val label: String, val hue: Float) {
    Dynamic("跟随壁纸", -1f),
    Blue("经典蓝", 212f),
    Purple("典雅紫", 268f),
    Teal("松石绿", 172f),
    Amber("暖琥珀", 38f),
    Rose("玫瑰粉", 336f),
}

/**
 * Applies the Material 3 colour scheme chosen in settings.
 *
 * The system bar icons are flipped to match, otherwise the status bar would keep its
 * dark-theme white icons and disappear against a light background.
 */
@Composable
fun AppTheme(
    mode: ThemeMode,
    palette: ThemePalette,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val context = LocalContext.current
    val scheme = when {
        palette == ThemePalette.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> tonalScheme(hue = palette.hue.takeIf { it >= 0f } ?: ThemePalette.Blue.hue, dark = dark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/*
 * Semantic colours for the readouts, expressed in terms of the active scheme so they
 * follow the player's theme choice instead of being baked to one dark palette.
 */
val Accent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

val Danger: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

/** Amber in either mode; the scheme's tertiary is a complement, not a warning. */
val Warning: Color
    @Composable @ReadOnlyComposable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
            Color(0xFFFFB74D)
        } else {
            Color(0xFFB26A00)
        }

/**
 * Builds a full Material 3 scheme from a single hue.
 *
 * Hand-picking every role for six palettes times two modes would be unmaintainable, so
 * the roles are derived the way Material's tonal palettes are: a saturated accent, a
 * muted secondary, a complementary tertiary, and near-neutral surfaces that carry just
 * enough of the hue to feel related.
 */
private fun tonalScheme(hue: Float, dark: Boolean): ColorScheme {
    val accent = ((hue % 360f) + 360f) % 360f
    val complement = (accent + 48f) % 360f

    return if (dark) {
        darkColorScheme(
            primary = Color.hsl(accent, 0.60f, 0.76f),
            onPrimary = Color.hsl(accent, 0.60f, 0.18f),
            primaryContainer = Color.hsl(accent, 0.48f, 0.32f),
            onPrimaryContainer = Color.hsl(accent, 0.52f, 0.92f),
            inversePrimary = Color.hsl(accent, 0.58f, 0.42f),
            secondary = Color.hsl(accent, 0.24f, 0.74f),
            onSecondary = Color.hsl(accent, 0.24f, 0.18f),
            secondaryContainer = Color.hsl(accent, 0.20f, 0.30f),
            onSecondaryContainer = Color.hsl(accent, 0.22f, 0.92f),
            tertiary = Color.hsl(complement, 0.46f, 0.74f),
            onTertiary = Color.hsl(complement, 0.46f, 0.18f),
            tertiaryContainer = Color.hsl(complement, 0.38f, 0.30f),
            onTertiaryContainer = Color.hsl(complement, 0.40f, 0.92f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color.hsl(accent, 0.14f, 0.07f),
            onBackground = Color.hsl(accent, 0.10f, 0.93f),
            surface = Color.hsl(accent, 0.14f, 0.08f),
            onSurface = Color.hsl(accent, 0.10f, 0.93f),
            surfaceVariant = Color.hsl(accent, 0.14f, 0.20f),
            onSurfaceVariant = Color.hsl(accent, 0.12f, 0.79f),
            outline = Color.hsl(accent, 0.10f, 0.46f),
            outlineVariant = Color.hsl(accent, 0.10f, 0.28f),
            inverseSurface = Color.hsl(accent, 0.10f, 0.93f),
            inverseOnSurface = Color.hsl(accent, 0.14f, 0.18f),
        )
    } else {
        lightColorScheme(
            primary = Color.hsl(accent, 0.56f, 0.42f),
            onPrimary = Color.hsl(accent, 0.40f, 0.99f),
            primaryContainer = Color.hsl(accent, 0.62f, 0.90f),
            onPrimaryContainer = Color.hsl(accent, 0.72f, 0.18f),
            inversePrimary = Color.hsl(accent, 0.62f, 0.76f),
            secondary = Color.hsl(accent, 0.22f, 0.44f),
            onSecondary = Color.hsl(accent, 0.20f, 0.99f),
            secondaryContainer = Color.hsl(accent, 0.30f, 0.90f),
            onSecondaryContainer = Color.hsl(accent, 0.32f, 0.16f),
            tertiary = Color.hsl(complement, 0.42f, 0.42f),
            onTertiary = Color.hsl(complement, 0.36f, 0.99f),
            tertiaryContainer = Color.hsl(complement, 0.50f, 0.90f),
            onTertiaryContainer = Color.hsl(complement, 0.56f, 0.16f),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = Color.hsl(accent, 0.45f, 0.985f),
            onBackground = Color.hsl(accent, 0.30f, 0.11f),
            surface = Color.hsl(accent, 0.45f, 0.99f),
            onSurface = Color.hsl(accent, 0.30f, 0.11f),
            surfaceVariant = Color.hsl(accent, 0.30f, 0.91f),
            onSurfaceVariant = Color.hsl(accent, 0.18f, 0.30f),
            outline = Color.hsl(accent, 0.14f, 0.48f),
            outlineVariant = Color.hsl(accent, 0.22f, 0.82f),
            inverseSurface = Color.hsl(accent, 0.20f, 0.18f),
            inverseOnSurface = Color.hsl(accent, 0.10f, 0.93f),
        )
    }
}
