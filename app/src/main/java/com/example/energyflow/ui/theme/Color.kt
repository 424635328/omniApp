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
val BackgroundDark = Color(0xFF0A0C14)     // 底层 — 近黑，微弱蓝调
val SurfaceDark = Color(0xFF131624)        // 表面层 — 深海军灰
val SurfaceVariant = Color(0xFF1A1E30)     // 卡片 — 略亮，层次分明
val OutlineDark = Color(0xFF252A3D)        // 边框 — 含蓄
val OutlineVariant = Color(0xFF1F2438)     // 浮层/弹窗 — 高出一档
// 新增: M3 表面变体
val SurfaceBrightDark = Color(0xFF1C2138)  // 抬高卡片
val SurfaceContainerDark = Color(0xFF161B2C)// 容器底色

// ── 亮色模式 · Pearl（暖白，干净现代） ──
val LightBackground = Color(0xFFF8F9FC)    // 底层 — 暖白
val LightSurface = Color(0xFFEFF1F7)       // 表面层 — 微灰
val LightCard = Color(0xFFFFFFFF)          // 卡片 — 纯白
val LightOutline = Color(0xFFDDE1EB)       // 边框 — 淡灰
// 新增: M3 表面变体
val SurfaceBrightLight = Color(0xFFFFFFFF) // 抬高卡片 — 纯白
val SurfaceContainerLight = Color(0xFFF0F2F8)// 容器底色

// ── 能源渐变端点（精炼） ──
val ElectricStart = Color(0xFF0098FF)      // 电光蓝
val ElectricEnd = Color(0xFF0058DD)        // 深蓝
val WaterStart = Color(0xFF00C8A0)         // 碧波青
val WaterEnd = Color(0xFF0078B0)           // 深海蓝
val GasStart = Color(0xFFFF7B3D)           // 暖橙
val GasEnd = Color(0xFFE03000)             // 赤焰红

// ── 语义色（暗/亮共用） ──
val ErrorNeon = Color(0xFFFF3B5C)          // 错误/删除
val WarningNeon = Color(0xFFFFB020)        // 警告
val SuccessGreen = Color(0xFF00D68F)        // 成功

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
val StaticPeakColor: Color = Color(0xFFFF8800)
val StaticValleyColor: Color = Color(0xFF8866DD)

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
val LocalAppSurfaceBright = compositionLocalOf { AppSurfaceBright }
val LocalAppSurfaceContainer = compositionLocalOf { AppSurfaceContainer }
val LocalAppTextPrimary = compositionLocalOf { AppTextPrimary }
val LocalAppTextSecondary = compositionLocalOf { AppTextSecondary }
val LocalAppTextTertiary = compositionLocalOf { AppTextTertiary }
