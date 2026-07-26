package com.example.energyflow.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════
// Obsidian & Pearl — 黑曜石暗色 / 珍珠亮色双色板
// 暗色层次深邃带蓝调，亮色温暖干净；主色与渐变端点精炼收敛
// ════════════════════════════════════════════

// ── 暗色模式 · Obsidian（近黑 + 蓝调基底） ──
val BackgroundDark = Color(0xFF080A12)     // 底层 — 深邃近黑，增强蓝调
val SurfaceDark = Color(0xFF111425)        // 表面层 — 深海军灰
val SurfaceVariant = Color(0xFF1B2035)     // 卡片 — 层次分明
val OutlineDark = Color(0xFF2A304A)        // 边框 — 清晰可辨
val OutlineVariant = Color(0xFF222842)     // 浮层/弹窗 — 高出一档
val SurfaceBrightDark = Color(0xFF202644)  // 抬高卡片
val SurfaceContainerDark = Color(0xFF15192C) // 容器底色

// ── 亮色模式 · Pearl（暖白，干净现代） ──
val LightBackground = Color(0xFFF5F6FA)    // 底层 — 暖白微蓝，避免纯白刺眼
val LightSurface = Color(0xFFEAECF4)       // 表面层 — 柔和灰蓝
val LightCard = Color(0xFFFFFFFF)          // 卡片 — 纯白，保持清晰
val LightOutline = Color(0xFFD8DCE6)       // 边框 — 淡灰蓝，更柔和
val SurfaceBrightLight = Color(0xFFFFFFFF) // 抬高卡片 — 纯白
val SurfaceContainerLight = Color(0xFFEEEFF6) // 容器底色 — 微蓝灰

// ── 能源渐变端点（精炼） ──
val ElectricStart = Color(0xFF00A8FF)      // 电光蓝 — 更明亮鲜活
val ElectricEnd = Color(0xFF0058DD)        // 深蓝 — 更深沉有力
val WaterStart = Color(0xFF00DDBB)         // 碧波青 — 更鲜艳生动
val WaterEnd = Color(0xFF007ABB)           // 深海蓝 — 更饱和沉稳
val GasStart = Color(0xFFFF8844)           // 暖橙 — 更温暖明亮
val GasEnd = Color(0xFFDD3300)             // 赤焰红 — 更浓郁热烈

// ── 语义色（暗/亮共用） ──
val ErrorNeon = Color(0xFFFF4466)          // 错误/删除 — 更鲜明
val WarningNeon = Color(0xFFFFBB33)        // 警告 — 更温暖
val SuccessGreen = Color(0xFF00DD99)        // 成功 — 更清新
val InfoBlue = Color(0xFF3399FF)           // 信息 — 新增
val HighlightYellow = Color(0xFFFFDD44)    // 高亮 — 新增

// ════════════════════════════════════════════
// 渐变 Brush — 缓存实例，避免每次访问重建
// ════════════════════════════════════════════
val ElectricGradient: Brush = Brush.linearGradient(listOf(ElectricStart, ElectricEnd))
val WaterGradient: Brush = Brush.linearGradient(listOf(WaterStart, WaterEnd))
val GasGradient: Brush = Brush.linearGradient(listOf(GasStart, GasEnd))

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
    val darkSurfaceBright: Color = SurfaceBrightDark,
    val darkSurfaceContainer: Color = SurfaceContainerDark,
    val lightBackground: Color = LightBackground,
    val lightSurface: Color = LightSurface,
    val lightCard: Color = LightCard,
    val lightSurfaceBright: Color = SurfaceBrightLight,
    val lightSurfaceContainer: Color = SurfaceContainerLight,
    val textPrimary: Color = Color(0xFFE2E8F0),
    val textSecondary: Color = Color(0xFF94A3B8),
    val textTertiary: Color = Color(0xFF64748B),
    // 新增: 亮色模式文字色（更高对比度）
    val lightTextPrimary: Color = Color(0xFF0F172A),
    val lightTextSecondary: Color = Color(0xFF475569),
    val lightTextTertiary: Color = Color(0xFF718096),
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
            textPrimary = colors.lightTextPrimary,
            textSecondary = colors.lightTextSecondary,
            textTertiary = colors.lightTextTertiary
        )
    }

    /** 根据当日最高温调整 UI 强调色，让 App 有"温度感"。 */
    fun applyWeatherTheme(tempMax: Double) {
        colors = colors.copy(electricColor = when {
            tempMax > 38 -> Color(0xFFFF4500)   // 酷暑红
            tempMax > 32 -> Color(0xFFFF8800)   // 炎热橙
            tempMax > 20 -> ElectricStart       // 常温电光蓝
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
val StaticPeakColor: Color = Color(0xFFFF9922)    // 峰电 — 更温暖明亮
val StaticValleyColor: Color = Color(0xFF9977EE)  // 谷电 — 更柔和优雅

/** 自动跟随暗/亮模式 */
val AppBackground: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkBackground else ThemeState.colors.lightBackground
val AppSurface: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkSurface else ThemeState.colors.lightSurface
val AppCard: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkCard else ThemeState.colors.lightCard
val AppSurfaceBright: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkSurfaceBright else ThemeState.colors.lightSurfaceBright
val AppSurfaceContainer: Color get() = if (ThemeState.colors.isDark) ThemeState.colors.darkSurfaceContainer else ThemeState.colors.lightSurfaceContainer
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
val NeonYellow: Color get() = ElectricStart    // 原名青绿，现为电光蓝
val NeonOrange: Color get() = StaticPeakColor
val NeonBlue: Color get() = WaterStart
val NeonCyan: Color get() = Color(0xFF4499FF)  // 更明亮
val NeonRed: Color get() = GasStart
val NeonPurple: Color get() = Color(0xFFAA22FF) // 更鲜艳

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
val LocalAppSurfaceBright = compositionLocalOf { AppSurfaceBright }
val LocalAppSurfaceContainer = compositionLocalOf { AppSurfaceContainer }
val LocalAppTextPrimary = compositionLocalOf { AppTextPrimary }
val LocalAppTextSecondary = compositionLocalOf { AppTextSecondary }
val LocalAppTextTertiary = compositionLocalOf { AppTextTertiary }

// 新增: 语义色 CompositionLocal
val LocalErrorColor = compositionLocalOf { ErrorNeon }
val LocalWarningColor = compositionLocalOf { WarningNeon }
val LocalSuccessColor = compositionLocalOf { SuccessGreen }
val LocalInfoColor = compositionLocalOf { InfoBlue }
