package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DeepSeekRepository 单元测试。
 *
 * 使用 Ktor MockEngine 模拟 HTTP 响应。
 * 使用 MockK 模拟 DeepSeekCredentialStore。
 */
class DeepSeekRepositoryTest {

    private val credentialStore = mockk<DeepSeekCredentialStore>(relaxUnitFun = true)

    /** 创建带 ContentNegotiation 的 Mock HttpClient，返回指定 JSON + 状态码。 */
    private fun buildMockClient(
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        engine {
            addHandler { request ->
                respond(
                    content = ByteReadChannel(json),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
    }

    // ── API Key 为空 ──

    @Test
    fun `blank api key returns null`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("")
        val repo = DeepSeekRepository(mockk(relaxed = true), credentialStore)
        assertNull(repo.analyze("test prompt"))
    }

    @Test
    fun `whitespace api key returns null`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("   ")
        val repo = DeepSeekRepository(mockk(relaxed = true), credentialStore)
        assertNull(repo.analyze("test prompt"))
    }

    // ── 成功响应 ──

    @Test
    fun `successful response returns content`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("sk-valid-key")

        val httpClient = buildMockClient("""
        {
            "choices": [
                {
                    "message": {
                        "role": "assistant",
                        "content": "您的能耗趋势平稳，建议关注峰电使用。"
                    }
                }
            ]
        }
        """.trimIndent())
        val repo = DeepSeekRepository(httpClient, credentialStore)

        val result = repo.analyze("分析一下")
        assertNotNull(result)
        assertEquals("您的能耗趋势平稳，建议关注峰电使用。", result)
    }

    @Test
    fun `successful response with multiple choices returns first`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("sk-valid-key")

        val httpClient = buildMockClient("""
        {
            "choices": [
                { "message": { "role": "assistant", "content": "第一个回答" } },
                { "message": { "role": "assistant", "content": "第二个回答" } }
            ]
        }
        """.trimIndent())
        val repo = DeepSeekRepository(httpClient, credentialStore)

        assertEquals("第一个回答", repo.analyze("分析"))
    }

    @Test
    fun `response with empty choices returns null`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("sk-valid-key")

        val httpClient = buildMockClient("""{ "choices": [] }""")
        val repo = DeepSeekRepository(httpClient, credentialStore)

        assertNull(repo.analyze("分析"))
    }

    @Test
    fun `response without message content returns null`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("sk-valid-key")

        val httpClient = buildMockClient("""
        {
            "choices": [
                { "message": { "role": "assistant", "content": null } }
            ]
        }
        """.trimIndent())
        val repo = DeepSeekRepository(httpClient, credentialStore)

        assertNull(repo.analyze("分析"))
    }

    // ── HTTP 错误 ──

    @Test
    fun `http error returns null silently`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("sk-valid-key")

        val httpClient = buildMockClient("""{"error": "unauthorized"}""", HttpStatusCode.Unauthorized)
        val repo = DeepSeekRepository(httpClient, credentialStore)

        assertNull(repo.analyze("分析"))
    }

    @Test
    fun `malformed json response returns null`() = runTest {
        every { credentialStore.apiKey } returns MutableStateFlow("sk-valid-key")

        val httpClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler { request ->
                    respond(
                        content = ByteReadChannel("not json at all"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain")
                    )
                }
            }
        }
        val repo = DeepSeekRepository(httpClient, credentialStore)

        assertNull(repo.analyze("分析"))
    }
}
