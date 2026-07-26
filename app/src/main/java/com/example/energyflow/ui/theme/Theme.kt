package com.example.energyflow.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.energyflow.data.ThemeDistColors
import kotlin.math.abs

/**
 * EnergyFlow 主题入口。
 *
 * ## 颜色策略（优先级由高到低）
 * 1. ThemeDist 服务器返回的每日主题色（`dynamicColors != null`）
 * 2. Material You 壁纸动态色（Android 12+，ThemeDist 关闭时）
 * 3. 默认 Obsidian/Pearl 色板（fallback）
 */
@Composable
fun EnergyFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColors: ThemeDistColors? = null,
    content: @Composable () -> Unit
) {
    val dc = dynamicColors
    val context = LocalContext.current

    // ── 确定品牌色 ──
    val primary: Color
    val secondary: Color
    val accent: Color
    val bg: Color
    val surface: Color
    val card: Color
    val textPrimary: Color
    val textSecondary: Color
    val textTertiary: Color
    val peakColor: Color
    val valleyColor: Color
    val waterColor: Color

    if (dc != null) {
        // ── 策略 1：ThemeDist 服务器每日主题 ──
        val dcSurface = dc.surface ?: SurfaceDark
        primary = dc.primary ?: NeonYellow
        secondary = dc.secondary ?: NeonOrange
        accent = dc.accent ?: dcSurface
        bg = dc.background ?: BackgroundDark
        surface = dcSurface
        card = lightenColor(surface, 0.10f)
        textPrimary = dc.text ?: if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF0F172A)
        textSecondary = dc.textMuted ?: if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
        textTertiary = if (darkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
        peakColor = shiftHue(primary, -40f)
        valleyColor = shiftHue(primary, 40f)
        waterColor = shiftHue(primary, 80f)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // ── 策略 2：Material You 壁纸动态色（Android 12+） ──
        val dynamicScheme = if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        primary = dynamicScheme.primary
        secondary = dynamicScheme.secondary
        accent = dynamicScheme.tertiary
        bg = dynamicScheme.background
        surface = dynamicScheme.surface
        card = dynamicScheme.surfaceVariant
        textPrimary = dynamicScheme.onBackground
        textSecondary = dynamicScheme.onSurfaceVariant
        textTertiary = dynamicScheme.outline
        peakColor = shiftHue(primary, -40f)
        valleyColor = shiftHue(primary, 40f)
        waterColor = shiftHue(primary, 80f)
    } else {
        // ── 策略 3：默认色板（fallback） ──
        primary = ElectricStart
        secondary = StaticPeakColor
        accent = if (darkTheme) SurfaceVariant else ElectricEnd
        bg = if (darkTheme) BackgroundDark else LightBackground
        surface = if (darkTheme) SurfaceDark else LightSurface
        card = if (darkTheme) SurfaceVariant else LightCard
        textPrimary = if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF0F172A)
        textSecondary = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
        textTertiary = if (darkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
        peakColor = StaticPeakColor
        valleyColor = StaticValleyColor
        waterColor = WaterStart
    }

    // ── 根据主色亮度确定 onPrimary 用黑还是白 ──
    fun isLight(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance > 0.5f
    }
    val onPrimaryColor = if (isLight(primary)) Color.Black else Color.White
    val onSecondaryColor = if (isLight(peakColor)) Color.Black else Color.White

    // ── 主色微染表面 — 极淡 primary 混入 card，形成品牌色系连贯感 ──
    val surfaceVariantTinted = lerp(card, primary, 0.04f)

    // ── M3 表面色调系统 — 从活跃 surface 推导 6 个层级 ──
    val surfaceDim: Color
    val surfaceBright: Color
    val surfaceContainerLowest: Color
    val surfaceContainer: Color
    val surfaceContainerHigh: Color
    val surfaceContainerHighest: Color
    if (darkTheme) {
        surfaceDim = lerp(surface, Color.Black, 0.25f)
        surfaceBright = lerp(surface, Color.White, 0.08f)
        surfaceContainerLowest = lerp(surface, Color.Black, 0.14f)
        surfaceContainer = lerp(surface, Color.White, 0.04f)
        surfaceContainerHigh = lerp(surface, Color.White, 0.10f)
        surfaceContainerHighest = lerp(surface, Color.White, 0.16f)
    } else {
        surfaceDim = lerp(surface, Color.Black, 0.06f)
        surfaceBright = lerp(surface, Color.White, 0.04f)
        surfaceContainerLowest = lerp(surface, Color.White, 0.02f)
        surfaceContainer = lerp(surface, Color.Black, 0.02f)
        surfaceContainerHigh = lerp(surface, Color.Black, 0.06f)
        surfaceContainerHighest = lerp(surface, Color.Black, 0.10f)
    }

    // ── 反转面 & 遮罩 ──
    val inverseSurface = if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF1A1E30)
    val inverseOnSurface = if (darkTheme) Color(0xFF1A1E30) else Color(0xFFE2E8F0)
    val inversePrimary = lerp(primary, Color.White, 0.50f)
    val scrim = Color.Black.copy(alpha = 0.50f)

    // ── 写入 ThemeState — 同时更新暗/亮两套字段，避免切换时残留旧值 ──
    ThemeState.colors = ThemeState.colors.copy(
        isDark = darkTheme,
        electricColor = primary,
        electricPeakColor = peakColor,
        electricValleyColor = valleyColor,
        waterColor = waterColor,
        gasColor = GasStart,
        darkBackground = if (darkTheme) bg else BackgroundDark,
        darkSurface = if (darkTheme) surface else SurfaceDark,
        darkCard = if (darkTheme) card else SurfaceVariant,
        darkSurfaceBright = if (darkTheme) surfaceBright else SurfaceBrightDark,
        darkSurfaceContainer = if (darkTheme) surfaceContainer else SurfaceContainerDark,
        lightBackground = if (!darkTheme) bg else LightBackground,
        lightSurface = if (!darkTheme) surface else LightSurface,
        lightCard = if (!darkTheme) card else LightCard,
        lightSurfaceBright = if (!darkTheme) surfaceBright else SurfaceBrightLight,
        lightSurfaceContainer = if (!darkTheme) surfaceContainer else SurfaceContainerLight,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textTertiary = textTertiary
    )

    // ── Material3 colorScheme（完整 M3 表面色调 + 反转 + 遮罩） ──
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimaryColor,
            primaryContainer = primary.copy(alpha = 0.15f),
            onPrimaryContainer = primary,
            inversePrimary = inversePrimary,
            secondary = peakColor,
            onSecondary = onSecondaryColor,
            secondaryContainer = peakColor.copy(alpha = 0.15f),
            onSecondaryContainer = peakColor,
            tertiary = accent,
            onTertiary = if (isLight(accent)) Color.Black else Color.White,
            background = bg,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceVariantTinted,
            onSurfaceVariant = textSecondary,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            outline = OutlineDark,
            outlineVariant = OutlineVariant,
            scrim = scrim,
            error = ErrorNeon,
            onError = Color.Black
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimaryColor,
            primaryContainer = primary.copy(alpha = 0.12f),
            onPrimaryContainer = primary,
            inversePrimary = inversePrimary,
            secondary = peakColor,
            onSecondary = onSecondaryColor,
            secondaryContainer = peakColor.copy(alpha = 0.12f),
            onSecondaryContainer = peakColor,
            tertiary = accent,
            onTertiary = if (isLight(accent)) Color.Black else Color.White,
            background = bg,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = card,
            onSurfaceVariant = textSecondary,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            outline = LightOutline,
            outlineVariant = LightOutline.copy(alpha = 0.5f),
            scrim = scrim,
            error = ErrorNeon,
            onError = Color.Black
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
        LocalAppCard provides card,
        LocalAppSurfaceBright provides surfaceBright,
        LocalAppSurfaceContainer provides surfaceContainer,
        LocalAppTextPrimary provides textPrimary,
        LocalAppTextSecondary provides textSecondary,
        LocalAppTextTertiary provides textTertiary,
        LocalErrorColor provides ErrorNeon,
        LocalWarningColor provides WarningNeon,
        LocalSuccessColor provides SuccessGreen,
        LocalInfoColor provides InfoBlue,
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            @Suppress("DEPRECATION")
            SideEffect {
                val window = (view.context as Activity).window
                // Edge-to-edge: 导航栏/状态栏透明，内容延伸到系统栏
                window.statusBarColor = bg.toArgb()
                window.navigationBarColor = bg.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = EnergyFlowTypography,
            shapes = EnergyFlowShapes,
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

/** 将颜色向白色方向插值以提亮。 */
private fun lightenColor(color: Color, fraction: Float): Color {
    return lerp(color, Color.White, fraction.coerceIn(0f, 1f))
}
