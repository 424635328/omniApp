package com.example.energyflow.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.plus

/**
 * 预测分析器核心（纯逻辑，跨平台可用）。
 *
 * 双重指数平滑 (DES) + 天气权重 + 周末因子。
 */
object PredictiveAnalyzerShared {

    private const val ALPHA = 0.3
    private const val BETA = 0.1
    private const val HEAT_THRESHOLD = 35.0
    private const val FORECAST_DAYS = 7
    private const val WEEKEND_BOOST = 1.15
    private const val MIN_DES_POINTS = 5

    data class Reading(
        val timestamp: LocalDateTime,
        val electricTotal: Double?
    )

    data class WeatherForecast(
        val date: LocalDate,
        val tempMax: Double
    )

    data class MonthPrediction(
        val dailyRateKwh: Double,
        val daysElapsed: Int,
        val daysRemaining: Int,
        val consumedSoFarKwh: Double,
        val predictedRemainingKwh: Double,
        val predictedTotalKwh: Double
    )

    fun predictMonth(
        records: List<Reading>,
        weatherForecast: List<WeatherForecast> = emptyList(),
        now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ): MonthPrediction? {
        val electricRecords = records
            .filter { it.electricTotal != null }
            .sortedBy { it.timestamp }

        if (electricRecords.size < 2) return null

        val clean = removeDecreasingReadings(electricRecords)
        if (clean.size < 2) return null

        val nowDate = now.date
        val monthStart = LocalDateTime(nowDate.year, nowDate.monthNumber, 1, 0, 0)
        val daysInMonth = daysInMonth(nowDate.year, nowDate.monthNumber)
        val monthEnd = LocalDateTime(nowDate.year, nowDate.monthNumber, daysInMonth, 23, 59)
        val daysElapsed = nowDate.dayOfMonth.coerceAtLeast(1)
        val daysRemaining = daysBetween(nowDate, monthEnd.date).coerceAtLeast(0)

        val monthRecords = clean.filter { it.timestamp >= monthStart }

        val dailyRate: Double
        val monthConsumptionSoFar: Double

        if (clean.size >= MIN_DES_POINTS) {
            val desResult = doubleExponentialSmooth(clean)
            dailyRate = desResult.trendAdjustedRate

            monthConsumptionSoFar = if (monthRecords.size >= 2) {
                val mFirst = monthRecords.first()
                val mLast = monthRecords.last()
                (mLast.electricTotal ?: 0.0) - (mFirst.electricTotal ?: 0.0)
            } else {
                dailyRate * daysElapsed
            }
        } else {
            return fallbackSimplePrediction(clean, monthRecords, daysElapsed, daysRemaining.toInt(), nowDate, now)
        }

        if (dailyRate <= 0) return null

        val weatherMultiplier = calculateWeatherMultiplier(nowDate, weatherForecast)
        val adjustedDailyRate = dailyRate * weatherMultiplier

        val weekendAdjustment = calculateWeekendFactor(nowDate, daysRemaining, clean)
        val finalDailyRate = adjustedDailyRate * weekendAdjustment

        val predictedRemaining = finalDailyRate * daysRemaining
        val predictedTotal = monthConsumptionSoFar + predictedRemaining

        return MonthPrediction(
            dailyRateKwh = finalDailyRate,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            consumedSoFarKwh = monthConsumptionSoFar,
            predictedRemainingKwh = predictedRemaining,
            predictedTotalKwh = predictedTotal
        )
    }

    private fun doubleExponentialSmooth(records: List<Reading>): DesResult {
        val dailyRates = buildDailyRateSeries(records)
        if (dailyRates.size < 3) {
            val simple = dailyRates.average()
            return DesResult(baseLevel = simple, trend = 0.0, trendAdjustedRate = simple)
        }

        var level = dailyRates[0]
        var trend = dailyRates.getOrNull(1)?.minus(dailyRates[0]) ?: 0.0

        for (i in 1 until dailyRates.size) {
            val prevLevel = level
            level = ALPHA * dailyRates[i] + (1 - ALPHA) * (level + trend)
            trend = BETA * (level - prevLevel) + (1 - BETA) * trend
        }

        val trendAdjustedRate = (level + trend * 0.5).coerceAtLeast(0.1)
        return DesResult(baseLevel = level, trend = trend, trendAdjustedRate = trendAdjustedRate)
    }

    private fun buildDailyRateSeries(records: List<Reading>): List<Double> {
        val rates = mutableListOf<Double>()
        for (i in 0 until records.size - 1) {
            val curr = records[i]
            val next = records[i + 1]
            val currTotal = curr.electricTotal ?: continue
            val nextTotal = next.electricTotal ?: continue
            if (nextTotal < currTotal) continue

            val days = daysBetween(curr.timestamp.date, next.timestamp.date).coerceAtLeast(1)
            val delta = nextTotal - currTotal
            rates.add(delta / days)
        }
        return rates
    }

    private fun calculateWeatherMultiplier(
        today: LocalDate,
        forecast: List<WeatherForecast>
    ): Double {
        if (forecast.isEmpty()) return 1.0

        val forecastEnd = today.plus(FORECAST_DAYS - 1, DateTimeUnit.DAY)
        val inWindow = forecast.filter { it.date >= today && it.date <= forecastEnd }
        if (inWindow.isEmpty()) return 1.0

        var totalDelta = 0.0
        for (day in inWindow) {
            val multiplier = when {
                day.tempMax >= 40.0 -> 1.5
                day.tempMax >= 38.0 -> 1.35
                day.tempMax >= HEAT_THRESHOLD -> 1.15
                else -> 1.0
            }
            totalDelta += multiplier - 1.0
        }
        return 1.0 + totalDelta / FORECAST_DAYS
    }

    private fun calculateWeekendFactor(
        today: LocalDate,
        daysRemaining: Int,
        records: List<Reading>
    ): Double {
        if (daysRemaining <= 2) return 1.0

        val remainingWeekends = (0 until daysRemaining).count { offset ->
            val day = today.plus(offset, DateTimeUnit.DAY)
            day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
        }
        val remainingWeekdays = daysRemaining - remainingWeekends

        val historicalFactor = calculateHistoricalWeekendFactor(records)
        val weightedFactor = (remainingWeekdays * 1.0 + remainingWeekends * historicalFactor) / daysRemaining
        return weightedFactor.coerceIn(0.9, 1.3)
    }

    private fun calculateHistoricalWeekendFactor(records: List<Reading>): Double {
        if (records.size < 4) return WEEKEND_BOOST

        var weekdayTotal = 0.0
        var weekdayCount = 0
        var weekendTotal = 0.0
        var weekendCount = 0

        for (i in 0 until records.size - 1) {
            val curr = records[i]
            val next = records[i + 1]
            val currTotal = curr.electricTotal ?: continue
            val nextTotal = next.electricTotal ?: continue
            if (nextTotal < currTotal) continue

            val days = daysBetween(curr.timestamp.date, next.timestamp.date)
            if (days <= 0 || days > 3) continue

            val daily = (nextTotal - currTotal) / days
            val dayOfWeek = curr.timestamp.dayOfWeek
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                weekendTotal += daily
                weekendCount++
            } else {
                weekdayTotal += daily
                weekdayCount++
            }
        }

        if (weekdayCount == 0 || weekendCount == 0) return WEEKEND_BOOST

        val weekdayAvg = weekdayTotal / weekdayCount
        val weekendAvg = weekendTotal / weekendCount
        return if (weekdayAvg > 0) (weekendAvg / weekdayAvg).coerceIn(0.8, 1.5) else WEEKEND_BOOST
    }

    private fun fallbackSimplePrediction(
        cleanRecords: List<Reading>,
        monthRecords: List<Reading>,
        daysElapsed: Int,
        daysRemaining: Int,
        nowDate: LocalDate,
        now: LocalDateTime
    ): MonthPrediction? {
        val dailyRate: Double
        val monthConsumptionSoFar: Double

        if (monthRecords.size >= 2) {
            val mFirst = monthRecords.first()
            val mLast = monthRecords.last()
            val elapsedMinutes = minutesBetween(mFirst.timestamp, mLast.timestamp)
            val actualDays = (elapsedMinutes / (24.0 * 60.0)).coerceAtLeast(1.0 / 24.0)
            val consumed = (mLast.electricTotal ?: 0.0) - (mFirst.electricTotal ?: 0.0)
            dailyRate = consumed / actualDays
            monthConsumptionSoFar = consumed
        } else {
            val windowSize = 5.coerceAtMost(cleanRecords.size)
            val recent = cleanRecords.takeLast(windowSize)
            val first = recent.first()
            val last = recent.last()
            val elapsedMinutes = minutesBetween(first.timestamp, last.timestamp)
            val totalDays = (elapsedMinutes / (24.0 * 60.0)).coerceAtLeast(1.0 / 24.0)
            val totalConsumption = (last.electricTotal ?: return null) - (first.electricTotal ?: return null)
            dailyRate = totalConsumption / totalDays
            monthConsumptionSoFar = dailyRate * daysElapsed
        }

        if (dailyRate <= 0) return null

        val predictedRemaining = dailyRate * daysRemaining
        val predictedTotal = monthConsumptionSoFar + predictedRemaining

        return MonthPrediction(
            dailyRateKwh = dailyRate,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            consumedSoFarKwh = monthConsumptionSoFar,
            predictedRemainingKwh = predictedRemaining,
            predictedTotalKwh = predictedTotal
        )
    }

    private fun removeDecreasingReadings(records: List<Reading>): List<Reading> {
        val clean = mutableListOf(records.first())
        for (i in 1 until records.size) {
            val prev = clean.last()
            val curr = records[i]
            val currTotal = curr.electricTotal ?: continue
            val prevTotal = prev.electricTotal ?: continue
            if (currTotal >= prevTotal) clean.add(curr)
        }
        return clean
    }

    private data class DesResult(
        val baseLevel: Double,
        val trend: Double,
        val trendAdjustedRate: Double
    )

    private fun daysBetween(from: LocalDate, to: LocalDate): Int {
        return (to.toEpochDays() - from.toEpochDays()).toInt()
    }

    private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Double {
        val fromSeconds = from.toInstant(TimeZone.currentSystemDefault()).epochSeconds
        val toSeconds = to.toInstant(TimeZone.currentSystemDefault()).epochSeconds
        return (toSeconds - fromSeconds) / 60.0
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        val thisMonth = LocalDate(year, month, 1)
        return (nextMonth.toEpochDays() - thisMonth.toEpochDays()).toInt()
    }
}
