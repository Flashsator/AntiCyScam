package com.anticyscam.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 反詐器主題：紅黑警示為主，刻意不跟隨 Material You 動態色，
// 確保不同裝置上警示視覺一致。
private val AntiScamDarkColors = darkColorScheme(
    primary = WarningRed,
    onPrimary = TextPrimary,
    primaryContainer = WarningRedDark,
    onPrimaryContainer = TextPrimary,

    secondary = AlertYellow,
    onSecondary = SurfaceBlack,

    tertiary = SuccessGreen,
    onTertiary = TextPrimary,

    background = SurfaceBlack,
    onBackground = TextPrimary,

    surface = SurfaceDim,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,

    error = WarningRedLight,
    onError = TextPrimary,

    outline = DividerGray,
    outlineVariant = DividerGray
)

private val AntiScamLightColors = lightColorScheme(
    primary = WarningRedDark,
    onPrimary = TextPrimary,
    background = SurfaceBlack,
    onBackground = TextPrimary,
    surface = SurfaceDim,
    onSurface = TextPrimary,
    error = WarningRed,
    onError = TextPrimary
)

@Composable
fun AntiCyScamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Anti-fraud branding is fixed; do not honor Material You by default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Always dark — the anti-fraud aesthetic is intentionally high-contrast.
        else -> AntiScamDarkColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SurfaceBlack.toArgb()
            window.navigationBarColor = SurfaceBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AntiScamTypography,
        content = content
    )
}
