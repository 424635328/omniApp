package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeepSeek API 客户端 — 全局能耗 AI 分析。
 *
 * 文档: https://api-docs.deepseek.com/
 * 模型: deepseek-chat (v4 flash)
 */
@Singleton
class DeepSeekRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val userPreferences: UserPreferences
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val API_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val MODEL = "deepseek-chat"
        private const val SYSTEM_PROMPT =
            "你是专业家庭能耗分析师。根据提供的完整数据给出简洁洞察。" +
            "用中文，聚焦：①整体趋势（升高/降低/稳定）②异常日期及可能原因 ③最大/最小消耗日 ④事件标签影响排序 ⑤具体节能建议。" +
            "限制 150 字以内，用条目式回答。"
    }

    /**
     * 调用 DeepSeek API 进行全局能耗分析。
     *
     * @param prompt 包含全部分析数据的提示词
     * @return AI 分析文本，失败或无 Key 时返回 null
     */
    suspend fun analyze(prompt: String): String? {
        val apiKey = userPreferences.deepSeekApiKey.first()
        if (apiKey.isBlank()) return null

        return try {
            val request = ChatRequest(
                model = MODEL,
                messages = listOf(
                    ChatMessage(role = "system", content = SYSTEM_PROMPT),
                    ChatMessage(role = "user", content = prompt)
                ),
                max_tokens = 300,
                temperature = 0.3
            )

            val response: ChatResponse = httpClient.post(API_URL) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(json.encodeToString(ChatRequest.serializer(), request))
            }.body()

            response.choices.firstOrNull()?.message?.content?.trim()
        } catch (e: Exception) {
            null
        }
    }
}

// ── DeepSeek API 数据模型 ──────────────────────────────

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 200,
    val temperature: Double = 0.3
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null
)
