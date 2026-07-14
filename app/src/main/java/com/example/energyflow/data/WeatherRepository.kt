package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open-Meteo 天气 API 客户端。
 *
 * 完全免费，无需 API Key。
 * 文档: https://open-meteo.com/en/docs
 *
 * 默认南京坐标 (32.06, 118.80)，可在设置中修改。
 *
 * 优化说明：
 * - 使用 @Serializable 数据类替代手动 JSON 遍历，类型安全
 * - 增加 weathercode（WMO 天气码→中文描述）和 precipitation_sum
 * - 自动检测 API 错误响应（error: true + reason）
 * - 验证数组长度一致性
 * - DailyWeather.date 改为 LocalDate，调用方无需反复解析字符串
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private const val ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"
        private const val DEFAULT_LAT = "32.06"
        private const val DEFAULT_LON = "118.80"

        /** 请求的 daily 参数字段列表。 */
        private const val DAILY_PARAMS =
            "temperature_2m_max,temperature_2m_min,weathercode,precipitation_sum"

        /** WMO 天气码 → 中文描述。 */
        private val WMO_DESCRIPTIONS = mapOf(
            0 to "晴",
            1 to "少云", 2 to "多云", 3 to "阴",
            45 to "雾", 48 to "大雾",
            51 to "小毛毛雨", 53 to "毛毛雨", 55 to "大毛毛雨",
            56 to "冻毛毛雨", 57 to "冻毛毛雨",
            61 to "小雨", 63 to "中雨", 65 to "大雨",
            66 to "冻雨", 67 to "冻雨",
            71 to "小雪", 73 to "中雪", 75 to "大雪",
            77 to "雪粒",
            80 to "小阵雨", 81 to "中阵雨", 82 to "大阵雨",
            85 to "小阵雪", 86 to "大阵雪",
            95 to "雷暴", 96 to "雷暴伴冰雹", 99 to "雷暴伴冰雹"
        )
    }

    // ── Open-Meteo JSON 响应结构（私有，不对外暴露） ──

    @Serializable
    private data class OpenMeteoResponse(
        val daily: DailyData? = null,
        val error: Boolean? = null,
        val reason: String? = null
    )

    @Serializable
    private data class DailyData(
        val time: List<String> = emptyList(),
        @SerialName("temperature_2m_max") val temperature2mMax: List<Double?>? = null,
        @SerialName("temperature_2m_min") val temperature2mMin: List<Double?>? = null,
        val weathercode: List<Int?>? = null,
        @SerialName("precipitation_sum") val precipitationSum: List<Double?>? = null,
    )

    // ── 公开 API ──

    /**
     * 获取 7 天天气预报。
     */
    suspend fun fetch7DayForecast(
        latitude: String = DEFAULT_LAT,
        longitude: String = DEFAULT_LON
    ): WeatherResult<List<DailyWeather>> {
        return try {
            val body: String = httpClient.get(FORECAST_URL) {
                url {
                    parameters.append("latitude", latitude)
                    parameters.append("longitude", longitude)
                    parameters.append("daily", DAILY_PARAMS)
                    parameters.append("timezone", "auto")
                    parameters.append("forecast_days", "7")
                }
            }.body()
            parseDailyResponse(body)
        } catch (e: Exception) {
            WeatherResult.Error("获取天气失败: ${e.message}")
        }
    }

    /**
     * 获取指定日期范围的历史天气。
     */
    suspend fun fetchHistorical(
        startDate: LocalDate,
        endDate: LocalDate,
        latitude: String = DEFAULT_LAT,
        longitude: String = DEFAULT_LON
    ): WeatherResult<List<DailyWeather>> {
        if (startDate.isAfter(endDate)) return WeatherResult.Error("日期范围无效")

        return try {
            val body: String = httpClient.get(ARCHIVE_URL) {
                url {
                    parameters.append("latitude", latitude)
                    parameters.append("longitude", longitude)
                    parameters.append("start_date", startDate.toString())
                    parameters.append("end_date", endDate.toString())
                    parameters.append("daily", DAILY_PARAMS)
                    parameters.append("timezone", "auto")
                }
            }.body()
            parseDailyResponse(body)
        } catch (e: Exception) {
            WeatherResult.Error("获取历史天气失败: ${e.message}")
        }
    }

    // ── 解析 ──

    /**
     * 解析 Open-Meteo JSON 响应为 [DailyWeather] 列表。
     *
     * 处理步骤：
     * 1. 反序列化为类型安全的响应对象
     * 2. 检测 API 显式错误（error: true + reason）
     * 3. 验证 daily 区块和数组长度一致性
     * 4. WMO 天气码 → 中文描述
     * 5. 过滤无效条目
     */
    internal fun parseDailyResponse(responseBody: String): WeatherResult<List<DailyWeather>> {
        val parsed = json.decodeFromString<OpenMeteoResponse>(responseBody)

        // Open-Meteo 错误响应: {"error":true,"reason":"..."}
        if (parsed.error == true) {
            val reason = parsed.reason ?: "未知错误"
            return WeatherResult.Error("Open-Meteo API 错误: $reason")
        }

        val daily = parsed.daily ?: return WeatherResult.Error("响应缺少 daily 数据")
        val times = daily.time
        if (times.isEmpty()) return WeatherResult.Error("无天气日期数据")

        // 验证数组长度一致性
        val size = times.size
        val maxTemps = daily.temperature2mMax
        val minTemps = daily.temperature2mMin
        val weatherCodes = daily.weathercode
        val precips = daily.precipitationSum

        if ((maxTemps != null && maxTemps.size != size) ||
            (minTemps != null && minTemps.size != size) ||
            (weatherCodes != null && weatherCodes.size != size) ||
            (precips != null && precips.size != size)
        ) {
            return WeatherResult.Error("天气数据字段长度不一致")
        }

        val results = mutableListOf<DailyWeather>()
        for (i in times.indices) {
            val date = try { LocalDate.parse(times[i]) } catch (_: Exception) { continue }
            val tempMax = maxTemps?.getOrNull(i) ?: continue
            val tempMin = minTemps?.getOrNull(i) ?: continue
            val code = weatherCodes?.getOrNull(i)
            val precip = precips?.getOrNull(i)

            results.add(DailyWeather(
                date = date,
                tempMax = tempMax,
                tempMin = tempMin,
                textDay = code?.let { WMO_DESCRIPTIONS[it] } ?: "",
                weatherCode = code,
                precipitation = precip
            ))
        }

        return if (results.isEmpty()) {
            WeatherResult.Error("未解析到有效的天气数据")
        } else {
            WeatherResult.Success(results)
        }
    }
}

/**
 * 单日天气数据。
 *
 * @param date 日期
 * @param tempMax 最高温度 (°C)
 * @param tempMin 最低温度 (°C)
 * @param textDay 天气描述（中文，如"晴"、"小雨"），来自 WMO 天气码映射
 * @param weatherCode WMO 天气码（0-99），可供图标绘制或条件判断
 * @param precipitation 降水量 (mm)，可为 null（无降水）
 */
data class DailyWeather(
    val date: LocalDate,
    val tempMax: Double,
    val tempMin: Double,
    val textDay: String = "",
    val weatherCode: Int? = null,
    val precipitation: Double? = null
)

sealed class WeatherResult<out T> {
    data class Success<T>(val data: T) : WeatherResult<T>()
    data class Error(val message: String) : WeatherResult<Nothing>()
}
