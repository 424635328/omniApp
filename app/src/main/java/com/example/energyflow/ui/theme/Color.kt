package com.example.energyflow.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════
// Midnight Slate — 暗黑石板 + 渐变霓虹
// ════════════════════════════════════════════

// 基础暗黑石板
val BackgroundDark = Color(0xFF0D0F12)     // 最底层背景
val SurfaceDark = Color(0xFF161A22)        // 卡片层
val SurfaceVariant = Color(0xFF1F242F)     // 悬浮层/BottomSheet
val OutlineDark = Color(0xFF333B4D)        // 边框/分割线

// 霓虹渐变基础色
val ElectricStart = Color(0xFF00FFC4)      // 荧光青绿
val ElectricEnd = Color(0xFF00B3FF)        // 电光蓝
val WaterStart = Color(0xFF00E5FF)         // 科技纯蓝
val WaterEnd = Color(0xFF0088FF)           // 深海蓝
val GasStart = Color(0xFFFF9100)           // 温暖橙
val GasEnd = Color(0xFFFF3D00)             // 赤焰红

// 特殊状态色
val ErrorNeon = Color(0xFFFF3366)          // 赛博红 — 删除/错误
val WarningNeon = Color(0xFFFFB020)        // 警示黄 — 警告

// 成功绿（保留，与霓虹体系独立）
val SuccessGreen = Color(0xFF00E676)

// ════════════════════════════════════════════
// 渐变 Brush — 用于装饰性元素
// ════════════════════════════════════════════
val ElectricGradient: Brush get() = Brush.linearGradient(listOf(ElectricStart, ElectricEnd))
val WaterGradient: Brush get() = Brush.linearGradient(listOf(WaterStart, WaterEnd))
val GasGradient: Brush get() = Brush.linearGradient(listOf(GasStart, GasEnd))

// ════════════════════════════════════════════
// 全局可变主题状态 — 单一 data class，一次重组
// ════════════════════════════════════════════
data class AppColors(
    val electricColor: Color = ElectricStart,
    val electricPeakColor: Color = Color(0xFFFF8800),
    val electricValleyColor: Color = Color(0xFF4488FF),
    val waterColor: Color = WaterStart,
    val gasColor: Color = GasStart,
    val isDark: Boolean = true,
    val darkBackground: Color = BackgroundDark,
    val darkSurface: Color = SurfaceDark,
    val darkCard: Color = SurfaceVariant,
    val lightBackground: Color = Color(0xFFF5F5F5),
    val lightSurface: Color = Color(0xFFEEEEEE),
    val lightCard: Color = Color(0xFFFFFFFF),
    val textPrimary: Color = Color(0xFFE2E8F0),
    val textSecondary: Color = Color(0xFF94A3B8),
    val textTertiary: Color = Color(0xFF64748B),
)

object ThemeState {
    var colors by mutableStateOf(AppColors())

    /** 根据当前模式切换文字色。 */
    fun applyMode() {
        colors = if (colors.isDark) colors.copy(
            textPrimary = Color(0xFFE2E8F0),
            textSecondary = Color(0xFF94A3B8),
            textTertiary = Color(0xFF64748B)
        ) else colors.copy(
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textTertiary = Color(0xFF94A3B8)
        )
    }

    /** 根据当日最高温调整 UI 强调色，让 App 有"温度感"。 */
    fun applyWeatherTheme(tempMax: Double) {
        colors = colors.copy(electricColor = when {
            tempMax > 38 -> Color(0xFFFF4500)
            tempMax > 32 -> Color(0xFFFF8800)
            tempMax > 20 -> ElectricStart
            tempMax > 10 -> Color(0xFF00B3FF)
            else -> Color(0xFF4488FF)
        })
    }
}

// ════════════════════════════════════════════
// 语义化顶层 val — 自动跟随 ThemeState.colors
// ════════════════════════════════════════════
val ElectricColor: Color get() = ThemeState.colors.electricColor
val ElectricPeakColor: Color get() = ThemeState.colors.electricPeakColor
val ElectricValleyColor: Color get() = ThemeState.colors.electricValleyColor
val WaterColor: Color get() = ThemeState.colors.waterColor
val GasColor: Color get() = ThemeState.colors.gasColor

/** 自动跟随暗/亮模式 */
val AppBackground: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkBackground else ThemeState.colors.lightBackground
val AppSurface: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkSurface else ThemeState.colors.lightSurface
val AppCard: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkCard else ThemeState.colors.lightCard
val AppTextPrimary: Color get() = ThemeState.colors.textPrimary
val AppTextSecondary: Color get() = ThemeState.colors.textSecondary
val AppTextTertiary: Color get() = ThemeState.colors.textTertiary

// ════════════════════════════════════════════
// 向后兼容别名 — 旧代码无需修改
// ════════════════════════════════════════════
val DarkBackground: Color get() = AppBackground
val DarkSurface: Color get() = AppSurface
val DarkCard: Color get() = AppCard
val TextPrimary: Color get() = AppTextPrimary
val TextSecondary: Color get() = AppTextSecondary
val TextTertiary: Color get() = AppTextTertiary

// 遗留霓虹名称 — 值已更新为新色板
val NeonYellow: Color get() = ElectricStart
val NeonOrange: Color get() = Color(0xFFFF8800)
val NeonBlue: Color get() = WaterStart
val NeonCyan: Color get() = Color(0xFF4488FF)
val NeonRed: Color get() = GasStart
val NeonPurple: Color get() = Color(0xFF9900FF)

// ════════════════════════════════════════════
// CompositionLocal
// ════════════════════════════════════════════
val LocalElectricColor = compositionLocalOf { ElectricColor }
val LocalElectricPeakColor = compositionLocalOf { ElectricPeakColor }
val LocalElectricValleyColor = compositionLocalOf { ElectricValleyColor }
val LocalWaterColor = compositionLocalOf { WaterColor }
val LocalGasColor = compositionLocalOf { GasColor }

val LocalAppBackground = compositionLocalOf { AppBackground }
val LocalAppSurface = compositionLocalOf { AppSurface }
val LocalAppCard = compositionLocalOf { AppCard }
val LocalAppTextPrimary = compositionLocalOf { AppTextPrimary }
val LocalAppTextSecondary = compositionLocalOf { AppTextSecondary }
val LocalAppTextTertiary = compositionLocalOf { AppTextTertiary }
