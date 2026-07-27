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
