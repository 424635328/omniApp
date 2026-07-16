package com.example.energyflow.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════
// Crystal — 通透晶石色板
// 暗色=中性干净基底，亮色=清爽微白；层次跨度大，主色 #00A3FF 负责出彩
// ════════════════════════════════════════════

// ── 暗色模式 · 晶石灰（中性，不偏色） ──
// 层间亮度差 ~10-15 点，视觉深度一目了然
val BackgroundDark = Color(0xFF0C0E14)     // 底层 — 深灰近乎黑，干净
val SurfaceDark = Color(0xFF171A26)        // 表面层 — 清晰跃升
val SurfaceVariant = Color(0xFF212538)     // 卡片/浮层 — 更进一步
val OutlineDark = Color(0xFF30364B)        // 边框 — 含蓄灰蓝
val OutlineVariant = Color(0xFF23283D)     // 弱化边框

// ── 亮色模式 · 晶石白（清爽微冷） ──
val LightBackground = Color(0xFFF6F8FC)    // 底层 — 微冷白
val LightSurface = Color(0xFFEDF0F6)       // 表面层 — 层次分明
val LightCard = Color(0xFFFFFFFF)          // 卡片 — 纯白

// ── 霓虹渐变基础色 ──
val ElectricStart = Color(0xFF00A3FF)      // 荧光蓝（主色）
val ElectricEnd = Color(0xFF0055FF)        // 电光深蓝
val WaterStart = Color(0xFF00D4AA)         // 碧波青
val WaterEnd = Color(0xFF0088CC)           // 深海蓝
val GasStart = Color(0xFFFF8C42)           // 暖橙
val GasEnd = Color(0xFFFF3D00)             // 赤焰红

// ── 语义色（暗色/亮色共用） ──
val ErrorNeon = Color(0xFFFF3366)          // 错误/删除 — 赛博红
val WarningNeon = Color(0xFFFFB020)        // 警告 — 琥珀黄
val SuccessGreen = Color(0xFF00E676)        // 成功 — 翠绿

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
    val electricPeakColor: Color = StaticPeakColor,
    val electricValleyColor: Color = StaticValleyColor,
    val waterColor: Color = WaterStart,
    val gasColor: Color = GasStart,
    val isDark: Boolean = true,
    val darkBackground: Color = BackgroundDark,
    val darkSurface: Color = SurfaceDark,
    val darkCard: Color = SurfaceVariant,
    val lightBackground: Color = LightBackground,
    val lightSurface: Color = LightSurface,
    val lightCard: Color = LightCard,
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
            tempMax > 38 -> Color(0xFFFF4500)   // 酷暑红
            tempMax > 32 -> Color(0xFFFF8800)   // 炎热橙
            tempMax > 20 -> ElectricStart       // 常温荧光蓝
            tempMax > 10 -> ElectricEnd         // 偏冷深蓝
            else -> ElectricValleyColor         // 寒冷蓝紫
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

// 静态语义色（不跟随主题变化）
val StaticPeakColor: Color = Color(0xFFFF8800)
val StaticValleyColor: Color = Color(0xFF8866DD)

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

// 遗留霓虹名称 — 保持向后兼容
val NeonYellow: Color get() = ElectricStart    // 原名青绿，现为荧光蓝
val NeonOrange: Color get() = StaticPeakColor
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
