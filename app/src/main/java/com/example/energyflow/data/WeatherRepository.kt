package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气免费 API 客户端。
 *
 * 免费 API key 需用户自行申请后填入设置。
 * 文档: https://dev.qweather.com/docs/api/weather/
 *
 * 数据模型（简化）：
 *   GET https://devapi.qweather.com/v7/weather/7d?location={cityId}&key={apikey}
 *   → daily[].tempMax, tempMin, date
 *
 * 也支持 historical weather:
 *   GET https://devapi.qweather.com/v7/historical/weather?location={cityId}&date={yyyyMMdd}&key={apikey}
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val userPreferences: UserPreferences
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取最近 7 天天气预报。
     *
     * @param cityId 城市 LocationID（如北京: 101010100）
     * @param apiKey 和风天气 API Key
     */
    suspend fun fetch7DayForecast(
        cityId: String = "101010100",
        apiKey: String = ""
    ): WeatherResult<List<DailyWeather>> {
        val key = apiKey.ifEmpty { return WeatherResult.Error("请先在设置中填入和风天气 API Key") }

        return try {
            val response: HttpResponse = httpClient.get(
                "https://devapi.qweather.com/v7/weather/7d"
            ) {
                url {
                    parameters.append("location", cityId)
                    parameters.append("key", key)
                }
            }

            val body = response.body<String>()
            val root = json.parseToJsonElement(body).jsonObject
            val code = root["code"]?.jsonPrimitive?.content

            if (code != "200") {
                return WeatherResult.Error("天气 API 返回错误: code=$code")
            }

            val dailyArray = root["daily"]?.let {
                json.decodeFromString<List<DailyWeatherDto>>(it.toString())
            } ?: return WeatherResult.Error("无天气数据")

            val dailies = dailyArray.map { dto ->
                DailyWeather(
                    date = dto.fxDate,
                    tempMax = dto.tempMax.toDoubleOrNull() ?: 0.0,
                    tempMin = dto.tempMin.toDoubleOrNull() ?: 0.0,
                    textDay = dto.textDay,
                    humidity = dto.humidity.toIntOrNull() ?: 0
                )
            }

            WeatherResult.Success(dailies)
        } catch (e: Exception) {
            WeatherResult.Error("获取天气失败: ${e.message}")
        }
    }

    /**
     * 获取指定日期范围的历史天气。
     * 用于与实际能耗数据对齐。
     */
    suspend fun fetchHistorical(
        startDate: LocalDate,
        endDate: LocalDate,
        cityId: String = "101010100",
        apiKey: String = ""
    ): WeatherResult<List<DailyWeather>> {
        val key = apiKey.ifEmpty { return WeatherResult.Error("请先在设置中填入和风天气 API Key") }
        val results = mutableListOf<DailyWeather>()

        var date = startDate
        while (!date.isAfter(endDate) && !date.isAfter(LocalDate.now().minusDays(1))) {
            try {
                val dateStr = date.toString().replace("-", "")
                val response: HttpResponse = httpClient.get(
                    "https://devapi.qweather.com/v7/historical/weather"
                ) {
                    url {
                        parameters.append("location", cityId)
                        parameters.append("date", dateStr)
                        parameters.append("key", key)
                    }
                }

                val body = response.body<String>()
                val root = json.parseToJsonElement(body).jsonObject
                val code = root["code"]?.jsonPrimitive?.content

                if (code == "200") {
                    val weatherDaily = root["weatherDaily"]?.let {
                        json.decodeFromString<List<DailyWeatherDto>>(it.toString())
                    }
                    weatherDaily?.forEach { dto ->
                        results.add(
                            DailyWeather(
                                date = dto.fxDate,
                                tempMax = dto.tempMax.toDoubleOrNull() ?: 0.0,
                                tempMin = dto.tempMin.toDoubleOrNull() ?: 0.0,
                                textDay = dto.textDay
                            )
                        )
                    }
                }
            } catch (_: Exception) { }

            date = date.plusDays(1)
        }

        return if (results.isEmpty()) {
            WeatherResult.Error("无法获取历史天气数据")
        } else {
            WeatherResult.Success(results)
        }
    }
}

// ── Data classes ───────────────────────────────────────────

@Serializable
data class DailyWeatherDto(
    val fxDate: String = "",
    val tempMax: String = "0",
    val tempMin: String = "0",
    val textDay: String = "",
    val humidity: String = "0"
)

data class DailyWeather(
    val date: String,       // yyyy-MM-dd
    val tempMax: Double,
    val tempMin: Double,
    val textDay: String = "",
    val humidity: Int = 0
)

sealed class WeatherResult<out T> {
    data class Success<T>(val data: T) : WeatherResult<T>()
    data class Error(val message: String) : WeatherResult<Nothing>()
}
