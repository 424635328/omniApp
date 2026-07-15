package com.example.energyflow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.energyflow.data.ThemeDistColors
import kotlin.math.abs

@Composable
fun EnergyFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColors: ThemeDistColors? = null,
    content: @Composable () -> Unit
) {
    val dc = dynamicColors

    // ── 品牌色（API 或默认霓虹） ──
    val primary = dc?.primary ?: NeonYellow
    val secondary = dc?.secondary ?: NeonOrange

    val peakColor = if (dc != null) shiftHue(dc.primary, -40f) else secondary
    val valleyColor = if (dc != null) shiftHue(dc.primary, 40f) else NeonCyan
    val waterColor = if (dc != null) shiftHue(dc.primary, 80f) else NeonBlue

    // ── 写入全局 ThemeState（单次 copy，一次重组） ──
    val baseCard = if (dc != null) Color(
        (dc.surface.red + 0.12f).coerceAtMost(1f),
        (dc.surface.green + 0.12f).coerceAtMost(1f),
        (dc.surface.blue + 0.12f).coerceAtMost(1f)
    ) else Color(0xFF2A2A2A)

    ThemeState.colors = ThemeState.colors.copy(
        isDark = darkTheme,
        electricColor = primary,
        electricPeakColor = peakColor,
        electricValleyColor = valleyColor,
        waterColor = waterColor,
        gasColor = NeonRed,
        darkBackground = dc?.background ?: Color(0xFF0A0A0A),
        darkSurface = dc?.surface ?: Color(0xFF1A1A1A),
        darkCard = baseCard,
        textPrimary = dc?.text ?: if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF0F172A),
        textSecondary = dc?.textMuted ?: if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
        textTertiary = if (darkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
    )

    // ── MaterialTheme colorScheme ──
    val bg = AppBackground
    val surface = AppSurface

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            secondary = peakColor,
            tertiary = dc?.accent ?: surface,
            background = bg,
            surface = surface,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = AppTextPrimary,
            onSurface = AppTextPrimary
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = peakColor,
            tertiary = dc?.accent ?: neonToLight(primary),
            background = bg,
            surface = surface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = AppTextPrimary,
            onSurface = AppTextPrimary
        )
    }

    CompositionLocalProvider(
        LocalElectricColor provides primary,
        LocalElectricPeakColor provides peakColor,
        LocalElectricValleyColor provides valleyColor,
        LocalWaterColor provides waterColor,
        LocalGasColor provides ThemeState.colors.gasColor,
        LocalAppBackground provides bg,
        LocalAppSurface provides surface,
        LocalAppCard provides AppCard,
        LocalAppTextPrimary provides AppTextPrimary,
        LocalAppTextSecondary provides AppTextSecondary,
        LocalAppTextTertiary provides AppTextTertiary,
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                window.statusBarColor = bg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = EnergyFlowTypography,
            content = content
        )
    }
}

// ═══════════════════════════════════════════════
// HSL 色相偏移
// ═══════════════════════════════════════════════

private fun shiftHue(color: Color, degrees: Float): Color {
    val r = color.red; val g = color.green; val b = color.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
    if (delta < 0.001f) return color
    var hue = when (max) {
        r -> ((g - b) / delta) % 6f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }
    hue *= 60f; if (hue < 0f) hue += 360f
    val lightness = (max + min) / 2f
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val newHue = ((hue + degrees) % 360f).let { if (it < 0f) it + 360f else it }
    return hslToRgb(newHue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

private fun hslToRgb(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val h = hue / 60f
    val x = c * (1f - abs(h % 2f - 1f))
    val (r1, g1, b1) = when {
        h < 1f  -> Triple(c, x, 0f)
        h < 2f  -> Triple(x, c, 0f)
        h < 3f  -> Triple(0f, c, x)
        h < 4f  -> Triple(0f, x, c)
        h < 5f  -> Triple(x, 0f, c)
        else    -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}

/** 霓虹色在浅色模式下加深，保证可读性 */
private fun neonToLight(neon: Color): Color {
    return Color(
        (neon.red * 0.55f).coerceAtLeast(0.15f),
        (neon.green * 0.55f).coerceAtLeast(0.15f),
        (neon.blue * 0.55f).coerceAtLeast(0.15f)
    )
}
