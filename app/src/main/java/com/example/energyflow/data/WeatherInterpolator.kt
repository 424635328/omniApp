package com.example.energyflow.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 天气数据线性插值器。
 *
 * 当 Open-Meteo 返回的天气数据未能覆盖某些消费记录日期时
 * 使用前后已知日期的温度值进行线性插值推导。
 *
 * 算法：
 * - 目标日期有数据 → 直接使用
 * - 目标日期在已知点之间 → 线性插值
 * - 目标日期在所有已知点之前/之后 → 最近邻外推
 */
object WeatherInterpolator {

    /**
     * 对目标日期列表进行天气数据插值。
     *
     * @param weatherData 原始天气数据（可能稀疏）
     * @param targetDates 需要天气数据的全部日期
     * @return 每个目标日期对应的 DailyWeather，包含插值或外推结果
     */
    fun interpolate(
        weatherData: List<DailyWeather>,
        targetDates: List<LocalDate>
    ): Map<LocalDate, DailyWeather> {
        if (weatherData.isEmpty() || targetDates.isEmpty()) return emptyMap()

        val sorted = weatherData
            .filter { it.tempMax > -900 && it.tempMin > -900 }
            .sortedBy { it.date }

        if (sorted.isEmpty()) return emptyMap()

        val weatherByDate = sorted.associateBy { it.date }
        val result = mutableMapOf<LocalDate, DailyWeather>()

        for (target in targetDates) {
            val existing = weatherByDate[target]
            if (existing != null) {
                result[target] = existing
                continue
            }

            val before = sorted.lastOrNull { it.date < target }
            val after = sorted.firstOrNull { it.date > target }

            val interpolated = when {
                before != null && after != null -> {
                    val span = ChronoUnit.DAYS.between(before.date, after.date).toDouble()
                    val pos = ChronoUnit.DAYS.between(before.date, target).toDouble()
                    val f = if (span > 0.0) (pos / span).coerceIn(0.0, 1.0) else 0.0

                    DailyWeather(
                        date = target,
                        tempMax = lerp(before.tempMax, after.tempMax, f),
                        tempMin = lerp(before.tempMin, after.tempMin, f),
                        textDay = if (f < 0.5) before.textDay else after.textDay,
                        weatherCode = if (f < 0.5) before.weatherCode else after.weatherCode,
                        precipitation = before.precipitation?.let { bp ->
                            after.precipitation?.let { ap -> lerp(bp, ap, f) }
                        }
                    )
                }
                before != null -> before.copy(date = target)
                after != null -> after.copy(date = target)
                else -> null
            }

            if (interpolated != null) {
                result[target] = interpolated
            }
        }

        return result
    }

    private fun lerp(a: Double, b: Double, fraction: Double): Double =
        a + (b - a) * fraction
}
