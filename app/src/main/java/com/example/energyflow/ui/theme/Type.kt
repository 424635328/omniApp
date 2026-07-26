package com.example.energyflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 文本使用平台无衬线字体保证中文可读性；数字读数继续使用等宽字体。
val AppFontFamily = FontFamily.SansSerif
val MonoFontFamily = FontFamily.Monospace

val EnergyFlowTypography = Typography(
    // ── 展示大 — 57sp，等宽用于大型数值仪表盘 ──
    displayLarge = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp  // 更紧凑，增强数字感
    ),
    // ── 展示中 — 45sp，等宽用于中型数值 ──
    displayMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp  // 更紧凑
    ),
    // ── 展示小 — 36sp，等宽用于紧凑数值 ──
    displaySmall = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.15).sp  // 更紧凑
    ),
    // ── 大标题 — 32-34sp，适合首页大型读数 ──
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp  // 更紧凑，增强标题感
    ),
    // ── 中标题 — 24-26sp，适合分区标题 ──
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.15).sp  // 更紧凑
    ),
    // ── 小标题 — 20-22sp，适合卡片标题 ──
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.1).sp  // 更紧凑
    ),
    // ── 大标题文本 — 18-20sp，适合对话框标题 ──
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // ── 中标题文本 — 16sp，适合列表项标题 ──
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    // ── 小标题文本 — 14sp，适合辅助标题 ──
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    // ── 正文大 — 16sp，舒适中文行高 ──
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp  // 更紧凑，提升阅读流畅度
    ),
    // ── 正文中 — 14sp ──
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp  // 更紧凑
    ),
    // ── 正文小 — 12sp ──
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp  // 更紧凑
    ),
    // ── 大标签 — 14sp，加重用于按钮/标签页 ──
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.05.sp  // 更紧凑
    ),
    // ── 中标签 — 12sp ──
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp  // 更紧凑
    ),
    // ── 小标签 — 11sp ──
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp  // 更紧凑
    )
)
