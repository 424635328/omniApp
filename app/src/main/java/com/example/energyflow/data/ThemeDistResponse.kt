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
 */
data class ThemeDistColors(
    val primary: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFFF00),
    val secondary: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFF6600),
    val accent: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF00BFFF),
    val background: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
    val surface: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    val text: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    val textMuted: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFB0B0B0),
    val border: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
)
