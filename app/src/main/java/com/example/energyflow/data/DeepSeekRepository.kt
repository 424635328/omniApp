package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AI 分析人设 — 决定 analyze() 使用的 system prompt 语气。
 * key 与 UserPreferences.aiPersona 持久化值对应。
 */
enum class AiPersona(val key: String) {
    ANALYST("analyst"),
    SASSY("sassy"),
    GENTLE("gentle");

    companion object {
        /** 未知 key 兜底为 ANALYST。 */
        fun fromKey(key: String): AiPersona = entries.firstOrNull { it.key == key } ?: ANALYST
    }
}

/**
 * DeepSeek API 客户端 — 全局能耗 AI 分析。
 *
 * 文档: https://api-docs.deepseek.com/
 * 模型: deepseek-chat (v4 flash)
 */
@Singleton
class DeepSeekRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val credentialStore: DeepSeekCredentialStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val API_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val MODEL = "deepseek-chat"
        private const val SYSTEM_PROMPT =
            "你是专业家庭能耗分析师。根据提供的完整数据给出简洁洞察。" +
                "用中文，聚焦：①整体趋势（升高/降低/稳定）②异常日期及可能原因 ③最大/最小消耗日 ④事件标签影响排序 ⑤具体节能建议。" +
                "限制 150 字以内，用条目式回答。"

        private const val SYSTEM_PROMPT_SASSY =
            "你是一位毒舌但靠谱的家庭能耗管家，说话犀利、爱吐槽，但分析绝不含糊。根据提供的完整数据给出简洁洞察。" +
                "用中文，聚焦：①整体趋势（升高/降低/稳定）②异常日期及可能原因 ③最大/最小消耗日 ④事件标签影响排序 ⑤具体节能建议。" +
                "语气犀利吐槽但信息必须完整，例如：\"你这周末开空调太狠了，二档电价在向你招手哦\"。" +
                "限制 150 字以内，用条目式回答。"

        private const val SYSTEM_PROMPT_GENTLE =
            "你是一位温柔体贴的家庭能耗小助手，语气温暖，善于鼓励用户。根据提供的完整数据给出简洁洞察。" +
                "用中文，聚焦：①整体趋势（升高/降低/稳定）②异常日期及可能原因 ③最大/最小消耗日 ④事件标签影响排序 ⑤具体节能建议。" +
                "用温暖鼓励的语气肯定用户的节能努力，建议要委婉贴心。" +
                "限制 150 字以内，用条目式回答。"

        private const val PARSE_SYSTEM_PROMPT =
            "你是一个能耗数据解析器。分析用户输入，提取结构化信息。\n" +
                "请严格按照以下格式回复（每行一个字段，用冒号分隔，无法识别的字段不输出）：\n" +
                "日期: 月.日格式，如7.15\n" +
                "时间: 时.分格式，如17.17\n" +
                "电表读数: 纯数字，如16776\n" +
                "水表读数: 纯数字，如880\n" +
                "备注: 如打开冰箱\n" +
                "示例输入: \"上周五看了电表16780\"\n" +
                "示例输出: 电表读数: 16780"
    }

    /**
     * 调用 DeepSeek API 进行全局能耗分析。
     *
     * @param prompt 包含全部分析数据的提示词
     * @param persona AI 助手人设，决定回答语气
     * @return AI 分析文本，失败或无 Key 时返回 null
     */
    suspend fun analyze(prompt: String, persona: AiPersona = AiPersona.ANALYST): String? {
        val apiKey = credentialStore.apiKey.value
        if (apiKey.isBlank()) return null

        val systemPrompt = when (persona) {
            AiPersona.ANALYST -> SYSTEM_PROMPT
            AiPersona.SASSY -> SYSTEM_PROMPT_SASSY
            AiPersona.GENTLE -> SYSTEM_PROMPT_GENTLE
        }

        return try {
            val request = ChatRequest(
                model = MODEL,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
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

    /**
     * DeepSeek 降级自然语言解析 — 当 SmartInputParser 正则匹配失败时调用。
     * 返回解析后的文本行（可直接传给 parseWithContext），方便二次解析。
     */
    suspend fun parseNaturalInput(input: String): String? {
        val apiKey = credentialStore.apiKey.value
        if (apiKey.isBlank()) return null

        return try {
            val request = ChatRequest(
                model = MODEL,
                messages = listOf(
                    ChatMessage(role = "system", content = PARSE_SYSTEM_PROMPT),
                    ChatMessage(role = "user", content = input)
                ),
                max_tokens = 100,
                temperature = 0.1
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
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatMessage? = null)
