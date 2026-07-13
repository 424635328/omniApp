package com.example.energyflow.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 天气数据线性插值器。
 *
 * 当 Open-Meteo 返回的天气数据未能覆盖某些消费记录日期时
 * （例如历史 API 覆盖范围有限、或某天数据缺失），
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
     * @param targetDates 需要天气数据的全部日期（通常是消费记录的日期）
     * @return 每个目标日期对应的 DailyWeather，包含插值或外推结果
     */
    fun interpolate(
        weatherData: List<DailyWeather>,
        targetDates: List<LocalDate>
    ): Map<LocalDate, DailyWeather> {
        if (weatherData.isEmpty() || targetDates.isEmpty()) return emptyMap()

        // 过滤无效数据并按日期排序
        val sorted = weatherData
            .filter { it.tempMax > -900 && it.tempMin > -900 }
            .sortedBy { it.date }

        if (sorted.isEmpty()) return emptyMap()

        val weatherByDate = sorted.associateBy { LocalDate.parse(it.date) }
        val result = mutableMapOf<LocalDate, DailyWeather>()

        for (target in targetDates) {
            // 已有精确数据
            val existing = weatherByDate[target]
            if (existing != null) {
                result[target] = existing
                continue
            }

            // 找前后最近邻
            val before = sorted.lastOrNull { LocalDate.parse(it.date) < target }
            val after = sorted.firstOrNull { LocalDate.parse(it.date) > target }

            val interpolated = when {
                // 双向线性插值
                before != null && after != null -> {
                    val bd = LocalDate.parse(before.date)
                    val ad = LocalDate.parse(after.date)
                    val span = ChronoUnit.DAYS.between(bd, ad).toDouble()
                    val pos = ChronoUnit.DAYS.between(bd, target).toDouble()
                    val f = if (span > 0.0) (pos / span).coerceIn(0.0, 1.0) else 0.0

                    DailyWeather(
                        date = target.toString(),
                        tempMax = lerp(before.tempMax, after.tempMax, f),
                        tempMin = lerp(before.tempMin, after.tempMin, f),
                        textDay = before.textDay,
                        humidity = Math.round(
                            lerp(before.humidity.toDouble(), after.humidity.toDouble(), f)
                        ).toInt()
                    )
                }
                // 单向外推
                before != null -> before.copy(date = target.toString())
                after != null -> after.copy(date = target.toString())
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
