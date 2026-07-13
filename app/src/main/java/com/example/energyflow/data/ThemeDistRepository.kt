package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeDistRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val userPreferences: UserPreferences
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val DAILY_URL = "https://themedist.netlify.app/api/v1/today.json"
    }

    /**
     * 从网络获取今日主题，成功时自动缓存原始 JSON 到 DataStore。
     */
    suspend fun fetchToday(): ThemeDistResponse? {
        return try {
            val body: String = httpClient.get(DAILY_URL).body()
            val response = json.decodeFromString<ThemeDistResponse>(body)
            // 缓存原始 JSON 供冷启动恢复
            userPreferences.cacheThemeJson(body)
            response
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 DataStore 缓存中恢复上一次成功的主题 JSON（冷启动时立即应用，避免闪烁）。
     */
    suspend fun loadCachedResponse(): ThemeDistResponse? {
        val cached = userPreferences.cachedThemeJson.first() ?: return null
        return try {
            json.decodeFromString<ThemeDistResponse>(cached)
        } catch (_: Exception) { null }
    }

    /**
     * 将 API 的 cssVars 解析为 Compose 颜色。
     */
    fun parseColors(response: ThemeDistResponse): ThemeDistColors {
        val vars = response.cssVars

        fun hexOrRgba(value: String?): androidx.compose.ui.graphics.Color? {
            if (value == null) return null
            return try {
                when {
                    value.startsWith("#") -> {
                        val hex = value.removePrefix("#")
                        when (hex.length) {
                            6 -> androidx.compose.ui.graphics.Color(
                                red = hex.substring(0, 2).toInt(16) / 255f,
                                green = hex.substring(2, 4).toInt(16) / 255f,
                                blue = hex.substring(4, 6).toInt(16) / 255f,
                                alpha = 1f
                            )
                            3 -> androidx.compose.ui.graphics.Color(
                                red = hex.substring(0, 1).repeat(2).toInt(16) / 255f,
                                green = hex.substring(1, 2).repeat(2).toInt(16) / 255f,
                                blue = hex.substring(2, 3).repeat(2).toInt(16) / 255f,
                                alpha = 1f
                            )
                            else -> null
                        }
                    }
                    value.startsWith("rgba(") -> {
                        val parts = value.removePrefix("rgba(").removeSuffix(")").split(",")
                        if (parts.size == 4) {
                            androidx.compose.ui.graphics.Color(
                                red = parts[0].trim().toFloat() / 255f,
                                green = parts[1].trim().toFloat() / 255f,
                                blue = parts[2].trim().toFloat() / 255f,
                                alpha = parts[3].trim().toFloat()
                            )
                        } else null
                    }
                    value.startsWith("rgb(") -> {
                        val parts = value.removePrefix("rgb(").removeSuffix(")").split(",")
                        if (parts.size == 3) {
                            androidx.compose.ui.graphics.Color(
                                red = parts[0].trim().toFloat() / 255f,
                                green = parts[1].trim().toFloat() / 255f,
                                blue = parts[2].trim().toFloat() / 255f,
                                alpha = 1f
                            )
                        } else null
                    }
                    else -> null
                }
            } catch (_: Exception) { null }
        }

        return ThemeDistColors(
            primary = hexOrRgba(vars["--color-primary"]) ?: ThemeDistColors().primary,
            secondary = hexOrRgba(vars["--color-secondary"]) ?: ThemeDistColors().secondary,
            accent = hexOrRgba(vars["--color-accent"]) ?: ThemeDistColors().accent,
            background = hexOrRgba(vars["--color-bg"]) ?: ThemeDistColors().background,
            surface = hexOrRgba(vars["--color-surface"]) ?: ThemeDistColors().surface,
            text = hexOrRgba(vars["--color-text"]) ?: ThemeDistColors().text,
            textMuted = hexOrRgba(vars["--color-text-muted"]) ?: ThemeDistColors().textMuted,
            border = hexOrRgba(vars["--color-border"]) ?: ThemeDistColors().border,
        )
    }
}
