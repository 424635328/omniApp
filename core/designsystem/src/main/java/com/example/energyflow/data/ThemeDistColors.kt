package com.example.energyflow.data

/**
 * 从 API cssVars 解析出的结构化主题颜色。
 * 默认值与 Obsidian 暗色色板对齐。
 */
data class ThemeDistColors(
    val primary: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF00A0FF),
    val secondary: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFF8800),
    val accent: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF00D4A8),
    val background: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF080A12),
    val surface: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF111425),
    val text: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFE2E8F0),
    val textMuted: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
    val border: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF2A304A),
)
