package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * ThemeDistRepository 单元测试。
 *
 * parseColors 是纯逻辑，无需 mock。
 * loadCachedResponse 需要 mock UserPreferences。
 */
class ThemeDistRepositoryTest {

    private val httpClient = mockk<HttpClient>(relaxed = true)
    private val prefs = mockk<UserPreferences>(relaxUnitFun = true)
    private lateinit var repository: ThemeDistRepository

    @Before
    fun setUp() {
        repository = ThemeDistRepository(httpClient, prefs)
    }

    // ── parseColors ──

    @Test
    fun `parseColors handles full 6-digit hex`() {
        val response = ThemeDistResponse(cssVars = mapOf(
            "--color-primary" to "#FF6600",
            "--color-secondary" to "#4FC3F7",
            "--color-accent" to "#00BFA5",
            "--color-bg" to "#1A1A2E",
            "--color-surface" to "#16213E",
            "--color-text" to "#E0E0E0",
            "--color-text-muted" to "#9E9E9E",
            "--color-border" to "#333333"
        ))
        val colors = repository.parseColors(response)
        // #FF6600 → R=1.0, G=0.4, B=0.0
        assertEquals(1.0f, colors.primary.red, 0.001f)
        assertEquals(0.4f, colors.primary.green, 0.001f)
        assertEquals(0.0f, colors.primary.blue, 0.001f)
        assertEquals(1.0f, colors.primary.alpha, 0.001f)
    }

    @Test
    fun `parseColors handles 3-digit hex`() {
        val response = ThemeDistResponse(cssVars = mapOf(
            "--color-primary" to "#F60",
            "--color-bg" to "#000",
            "--color-surface" to "#FFF",
            "--color-text" to "#AAA",
            "--color-text-muted" to "#999",
            "--color-border" to "#333",
            "--color-secondary" to "#4FC3F7",
            "--color-accent" to "#00BFA5"
        ))
        val colors = repository.parseColors(response)
        // #F60 → FF6600
        assertEquals(1.0f, colors.primary.red, 0.001f)
        assertEquals(0.4f, colors.primary.green, 0.001f)
        assertEquals(0.0f, colors.primary.blue, 0.001f)
    }

    @Test
    fun `parseColors handles rgba format`() {
        val response = ThemeDistResponse(cssVars = mapOf(
            "--color-primary" to "rgba(255,102,0,0.8)",
            "--color-bg" to "rgba(0,0,0,0.5)",
            "--color-surface" to "rgba(22,33,62,1)",
            "--color-text" to "rgba(224,224,224,0.9)",
            "--color-text-muted" to "rgba(158,158,158,0.7)",
            "--color-border" to "rgba(51,51,51,1)",
            "--color-secondary" to "#4FC3F7",
            "--color-accent" to "#00BFA5"
        ))
        val colors = repository.parseColors(response)
        assertEquals(1.0f, colors.primary.red, 0.001f)
        assertEquals(0.4f, colors.primary.green, 0.001f)
        assertEquals(0.0f, colors.primary.blue, 0.001f)
        assertEquals(0.8f, colors.primary.alpha, 0.001f)
    }

    @Test
    fun `parseColors handles rgb format`() {
        val response = ThemeDistResponse(cssVars = mapOf(
            "--color-primary" to "rgb(255,102,0)",
            "--color-bg" to "rgb(26,26,46)",
            "--color-surface" to "rgb(22,33,62)",
            "--color-text" to "rgb(224,224,224)",
            "--color-text-muted" to "rgb(158,158,158)",
            "--color-border" to "rgb(51,51,51)",
            "--color-secondary" to "#4FC3F7",
            "--color-accent" to "#00BFA5"
        ))
        val colors = repository.parseColors(response)
        assertEquals(1.0f, colors.primary.red, 0.001f)
        assertEquals(0.4f, colors.primary.green, 0.001f)
        assertEquals(0.0f, colors.primary.blue, 0.001f)
        assertEquals(1.0f, colors.primary.alpha, 0.001f)
    }

    @Test
    fun `parseColors falls back to defaults for invalid colors`() {
        val response = ThemeDistResponse(cssVars = mapOf(
            "--color-primary" to "not-a-color",
            "--color-bg" to "#GGG",
            "--color-surface" to "",
            "--color-text" to "rgb(invalid)",
            "--color-text-muted" to "rgba(bad)",
            "--color-border" to "bad",
            "--color-secondary" to "#4FC3F7",
            "--color-accent" to "#00BFA5"
        ))
        val colors = repository.parseColors(response)
        // 应当回退到默认值
        assertNotNull(colors.primary)
        assertNotNull(colors.background)
    }

    @Test
    fun `parseColors with empty cssVars returns defaults`() {
        val response = ThemeDistResponse(cssVars = emptyMap())
        val colors = repository.parseColors(response)
        val defaults = ThemeDistColors()
        assertEquals(defaults.primary, colors.primary)
        assertEquals(defaults.secondary, colors.secondary)
        assertEquals(defaults.accent, colors.accent)
        assertEquals(defaults.background, colors.background)
        assertEquals(defaults.surface, colors.surface)
        assertEquals(defaults.text, colors.text)
        assertEquals(defaults.textMuted, colors.textMuted)
        assertEquals(defaults.border, colors.border)
    }

    @Test
    fun `parseColors with empty cssVars map returns defaults`() {
        val response = ThemeDistResponse(cssVars = emptyMap())
        val colors = repository.parseColors(response)
        val defaults = ThemeDistColors()
        assertEquals(defaults.primary, colors.primary)
    }

    // ── loadCachedResponse ──

    @Test
    fun `loadCachedResponse returns null when no cache`() = runTest {
        coEvery { prefs.cachedThemeJson } returns flowOf(null)
        assertNull(repository.loadCachedResponse())
    }

    @Test
    fun `loadCachedResponse returns parsed response from cache`() = runTest {
        val json = """{
            "cssVars": {
                "--color-primary": "#FF6600",
                "--color-secondary": "#4FC3F7",
                "--color-accent": "#00BFA5",
                "--color-bg": "#1A1A2E",
                "--color-surface": "#16213E",
                "--color-text": "#E0E0E0",
                "--color-text-muted": "#9E9E9E",
                "--color-border": "#333333"
            }
        }""".trimIndent()
        coEvery { prefs.cachedThemeJson } returns flowOf(json)

        val result = repository.loadCachedResponse()
        assertNotNull(result)
        assertEquals("#FF6600", result!!.cssVars["--color-primary"])
    }

    @Test
    fun `loadCachedResponse returns null when cache has invalid json`() = runTest {
        coEvery { prefs.cachedThemeJson } returns flowOf("not valid json {")
        assertNull(repository.loadCachedResponse())
    }
}
