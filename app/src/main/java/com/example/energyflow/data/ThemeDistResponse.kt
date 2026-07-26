package com.example.energyflow.data

import kotlinx.serialization.Serializable

@Serializable
data class ThemeDistResponse(
    val date: String = "",
    val generatedAt: String = "",
    val preset: String = "",
    val presetName: String? = null,
    val cssVars: Map<String, String> = emptyMap(),
    val customCss: String? = null,
    val extensions: List<ThemeDistExtension> = emptyList(),
    val available: Int = 0,
    val directory: List<ThemeDistPreset> = emptyList(),
    val dailyIsCommunity: Boolean = false,
    val apiVersion: String = "v1"
)

@Serializable
data class ThemeDistExtension(
    val type: String = "",
    val html: String? = null,
    val char: String? = null,
    val top: String? = null,
    val left: String? = null,
    val right: String? = null,
    val bottom: String? = null,
    val fontSize: String? = null,
    val opacity: Double? = null,
    val animation: String? = null,
    val zIndex: Int? = null
)

@Serializable
data class ThemeDistPreset(
    val preset: String = "",
    val name: String? = null,
    val primary: String = "",
    val accent: String = "",
    val logoText: String? = null,
    val community: Boolean? = null
)

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
