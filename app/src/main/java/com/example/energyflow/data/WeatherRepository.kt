package com.example.energyflow.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private const val ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"
        // 南京默认坐标
        private const val DEFAULT_LAT = "32.06"
        private const val DEFAULT_LON = "118.80"
    }

    /**
     * 获取 7 天天气预报。
     */
    suspend fun fetch7DayForecast(
        latitude: String = DEFAULT_LAT,
        longitude: String = DEFAULT_LON
    ): WeatherResult<List<DailyWeather>> {
        return try {
            val response: HttpResponse = httpClient.get(FORECAST_URL) {
                url {
                    parameters.append("latitude", latitude)
                    parameters.append("longitude", longitude)
                    parameters.append("daily", "temperature_2m_max,temperature_2m_min")
                    parameters.append("timezone", "auto")
                    parameters.append("forecast_days", "7")
                }
            }
            parseDailyResponse(response)
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
            val response: HttpResponse = httpClient.get(ARCHIVE_URL) {
                url {
                    parameters.append("latitude", latitude)
                    parameters.append("longitude", longitude)
                    parameters.append("start_date", startDate.toString())
                    parameters.append("end_date", endDate.toString())
                    parameters.append("daily", "temperature_2m_max,temperature_2m_min")
                    parameters.append("timezone", "auto")
                }
            }
            parseDailyResponse(response)
        } catch (e: Exception) {
            WeatherResult.Error("获取历史天气失败: ${e.message}")
        }
    }

    private suspend fun parseDailyResponse(response: HttpResponse): WeatherResult<List<DailyWeather>> {
        val body = response.body<String>()
        val root = json.parseToJsonElement(body).jsonObject
        val daily = root["daily"]?.jsonObject
            ?: return WeatherResult.Error("无天气数据")

        val times = daily["time"]?.jsonArray
            ?: return WeatherResult.Error("缺少日期数据")
        val maxTemps = daily["temperature_2m_max"]?.jsonArray
            ?: return WeatherResult.Error("缺少最高温数据")

        val results = mutableListOf<DailyWeather>()
        for (i in times.indices) {
            val date = times[i].jsonPrimitive.content
            val tempMax = maxTemps.getOrNull(i)?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val tempMin = daily["temperature_2m_min"]?.jsonArray
                ?.getOrNull(i)?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            results.add(DailyWeather(date = date, tempMax = tempMax, tempMin = tempMin))
        }

        return if (results.isEmpty()) {
            WeatherResult.Error("无天气数据")
        } else {
            WeatherResult.Success(results)
        }
    }
}

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
