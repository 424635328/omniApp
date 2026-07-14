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
// 全局可变主题状态
// ════════════════════════════════════════════
object ThemeState {
    // 品牌色
    var electricColor by mutableStateOf(ElectricStart)
    var electricPeakColor by mutableStateOf(Color(0xFFFF8800))
    var electricValleyColor by mutableStateOf(Color(0xFF4488FF))
    var waterColor by mutableStateOf(WaterStart)
    var gasColor by mutableStateOf(GasStart)

    // 是否为深色模式
    var isDark by mutableStateOf(true)

    // 深色（Midnight Slate）
    var darkBackground by mutableStateOf(BackgroundDark)
    var darkSurface by mutableStateOf(SurfaceDark)
    var darkCard by mutableStateOf(SurfaceVariant)

    // 浅色
    var lightBackground by mutableStateOf(Color(0xFFF5F5F5))
    var lightSurface by mutableStateOf(Color(0xFFEEEEEE))
    var lightCard by mutableStateOf(Color(0xFFFFFFFF))

    // 文字
    var textPrimary by mutableStateOf(Color(0xFFE2E8F0))
    var textSecondary by mutableStateOf(Color(0xFF94A3B8))
    var textTertiary by mutableStateOf(Color(0xFF64748B))

    /** 根据当前模式切换文字色。 */
    fun applyMode() {
        if (isDark) {
            textPrimary = Color(0xFFE2E8F0)
            textSecondary = Color(0xFF94A3B8)
            textTertiary = Color(0xFF64748B)
        } else {
            textPrimary = Color(0xFF0F172A)
            textSecondary = Color(0xFF475569)
            textTertiary = Color(0xFF94A3B8)
        }
    }
}

// ════════════════════════════════════════════
// 语义化顶层 val — 自动跟随 isDark & ThemeState
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
