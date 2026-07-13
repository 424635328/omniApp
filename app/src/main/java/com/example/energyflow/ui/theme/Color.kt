package com.example.energyflow.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════
// 霓虹品牌色 — 暗/亮主题通用
// ════════════════════════════════════════════
val NeonYellow = Color(0xFFFFFF00)
val NeonOrange = Color(0xFFFF6600)
val NeonBlue = Color(0xFF00BFFF)
val NeonCyan = Color(0xFF00FFFF)
val NeonRed = Color(0xFFFF0066)
val NeonPurple = Color(0xFF9900FF)

// ════════════════════════════════════════════
// 全局可变主题状态
// ════════════════════════════════════════════
object ThemeState {
    // 品牌色
    var electricColor by mutableStateOf(NeonYellow)
    var electricPeakColor by mutableStateOf(NeonOrange)
    var electricValleyColor by mutableStateOf(NeonCyan)
    var waterColor by mutableStateOf(NeonBlue)
    var gasColor by mutableStateOf(NeonRed)

    // 是否为深色模式
    var isDark by mutableStateOf(true)

    // 深色
    var darkBackground by mutableStateOf(Color(0xFF0A0A0A))
    var darkSurface by mutableStateOf(Color(0xFF1A1A1A))
    var darkCard by mutableStateOf(Color(0xFF2A2A2A))

    // 浅色
    var lightBackground by mutableStateOf(Color(0xFFF5F5F5))
    var lightSurface by mutableStateOf(Color(0xFFEEEEEE))
    var lightCard by mutableStateOf(Color(0xFFFFFFFF))

    // 文字
    var textPrimary by mutableStateOf(Color.White)
    var textSecondary by mutableStateOf(Color(0xFFB0B0B0))
    var textTertiary by mutableStateOf(Color(0xFF808080))

    /** 根据当前模式返回对应的背景/表面/卡片/文字色。Theme.kt 切换 isDark 后调用。 */
    fun applyMode() {
        textPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
        textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666)
        textTertiary = if (isDark) Color(0xFF808080) else Color(0xFF999999)
    }
}

// ════════════════════════════════════════════
// 语义化顶层 val — 自动跟随 isDark
// ════════════════════════════════════════════
val ElectricColor: Color get() = ThemeState.electricColor
val ElectricPeakColor: Color get() = ThemeState.electricPeakColor
val ElectricValleyColor: Color get() = ThemeState.electricValleyColor
val WaterColor: Color get() = ThemeState.waterColor
val GasColor: Color get() = ThemeState.gasColor

/** 自动跟随暗/亮模式 */
val AppBackground: Color get() = if (ThemeState.isDark) ThemeState.darkBackground else ThemeState.lightBackground
val AppSurface: Color get() = if (ThemeState.isDark) ThemeState.darkSurface else ThemeState.lightSurface
val AppCard: Color get() = if (ThemeState.isDark) ThemeState.darkCard else ThemeState.lightCard
val AppTextPrimary: Color get() = ThemeState.textPrimary
val AppTextSecondary: Color get() = ThemeState.textSecondary
val AppTextTertiary: Color get() = ThemeState.textTertiary

// ════════════════════════════════════════════
// 向后兼容别名（旧代码无需修改）
// ════════════════════════════════════════════
val DarkBackground: Color get() = AppBackground
val DarkSurface: Color get() = AppSurface
val DarkCard: Color get() = AppCard
val TextPrimary: Color get() = AppTextPrimary
val TextSecondary: Color get() = AppTextSecondary
val TextTertiary: Color get() = AppTextTertiary

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
