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
 * 升级版：多重预测模型集成
 * - 双重指数平滑 (DES / Holt) — 趋势跟踪
 * - 三重指数平滑 (Holt-Winters) — 季节性分解
 * - 线性回归 — 长期趋势检测
 * - 自回归移动平均 (AR) — 短期波动捕捉
 * - 天气权重 + 周末因子 — 外部变量修正
 * - 置信区间 — 预测不确定性量化
 */
object PredictiveAnalyzerShared {

    // ── 模型参数 ──
    private const val ALPHA = 0.3          // 水平平滑系数
    private const val BETA = 0.1           // 趋势平滑系数
    private const val GAMMA = 0.15         // 季节性平滑系数
    private const val HEAT_THRESHOLD = 35.0
    private const val COLD_THRESHOLD = 5.0
    private const val FORECAST_DAYS = 7
    private const val WEEKEND_BOOST = 1.15
    private const val MIN_DES_POINTS = 5
    private const val MIN_SEASONAL_POINTS = 14  // 至少2周数据才能检测季节性
    private const val AR_LAG = 3               // 自回归阶数
    private const val SEASON_LENGTH = 7        // 周季节性
    private const val MIN_LR_POINTS = 6        // 线性回归最小数据点
    private const val MIN_AR_POINTS = 7        // 自回归最小数据点

    data class Reading(
        val timestamp: LocalDateTime,
        val electricTotal: Double?
    )

    data class WeatherForecast(
        val date: LocalDate,
        val tempMax: Double,
        val tempMin: Double = tempMax - 10.0  // 新增最低温
    )

    data class MonthPrediction(
        val dailyRateKwh: Double,
        val daysElapsed: Int,
        val daysRemaining: Int,
        val consumedSoFarKwh: Double,
        val predictedRemainingKwh: Double,
        val predictedTotalKwh: Double,
        // 新增：置信区间和模型信息
        val confidenceLow: Double = predictedTotalKwh * 0.85,
        val confidenceHigh: Double = predictedTotalKwh * 1.15,
        val modelUsed: String = "DES",
        val seasonalFactor: Double = 1.0,
        val trendDirection: TrendDirection = TrendDirection.STABLE
    )

    enum class TrendDirection { RISING, FALLING, STABLE }

    /**
     * 预测当月用电量。
     * 集成多重模型，自动选择最佳预测策略。
     */
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

        // ── 零消耗提前返回 ──
        val totalConsumption = (clean.last().electricTotal ?: 0.0) - (clean.first().electricTotal ?: 0.0)
        if (totalConsumption <= 0) return null

        // ── 多模型预测 ──
        val predictions = mutableListOf<ModelPrediction>()

        // 模型1: DES (双重指数平滑)
        if (clean.size >= MIN_DES_POINTS) {
            val desResult = doubleExponentialSmooth(clean)
            if (desResult.trendAdjustedRate > 0) {
                predictions.add(ModelPrediction(
                    model = "DES",
                    dailyRate = desResult.trendAdjustedRate,
                    confidence = 0.8
                ))
            }
        }

        // 模型2: Holt-Winters (三重指数平滑，季节性)
        if (clean.size >= MIN_SEASONAL_POINTS) {
            val hwResult = holtWintersSmooth(clean)
            if (hwResult != null && hwResult.dailyRate > 0) {
                predictions.add(ModelPrediction(
                    model = "Holt-Winters",
                    dailyRate = hwResult.dailyRate,
                    confidence = 0.85,
                    seasonalFactor = hwResult.seasonalFactor
                ))
            }
        }

        // 模型3: 线性回归（需要更多数据点才激活）
        if (clean.size >= MIN_LR_POINTS) {
            val lrResult = linearRegression(clean)
            if (lrResult != null && lrResult.dailyRate > 0) {
                predictions.add(ModelPrediction(
                    model = "Linear Regression",
                    dailyRate = lrResult.dailyRate,
                    confidence = 0.7
                ))
            }
        }

        // 模型4: AR (自回归)（需要更多数据点才激活）
        if (clean.size >= MIN_AR_POINTS) {
            val arResult = autoRegressive(clean)
            if (arResult != null && arResult > 0) {
                predictions.add(ModelPrediction(
                    model = "AR",
                    dailyRate = arResult,
                    confidence = 0.65
                ))
            }
        }

        // 模型5: 简单移动平均 (fallback)
        if (predictions.isEmpty()) {
            return fallbackSimplePrediction(clean, monthRecords, daysElapsed, daysRemaining, nowDate, now)
        }

        // ── 集成预测：加权平均 ──
        val totalConfidence = predictions.sumOf { it.confidence }
        val weightedDailyRate = predictions.sumOf { it.dailyRate * it.confidence } / totalConfidence

        // ── 计算当月已消耗 ──
        val monthConsumptionSoFar = if (monthRecords.size >= 2) {
            val mFirst = monthRecords.first()
            val mLast = monthRecords.last()
            (mLast.electricTotal ?: 0.0) - (mFirst.electricTotal ?: 0.0)
        } else {
            weightedDailyRate * daysElapsed
        }

        // ── 天气修正 ──
        val weatherMultiplier = calculateWeatherMultiplier(nowDate, weatherForecast)
        val adjustedDailyRate = weightedDailyRate * weatherMultiplier

        // ── 周末因子 ──
        val weekendAdjustment = calculateWeekendFactor(nowDate, daysRemaining, clean)
        val finalDailyRate = adjustedDailyRate * weekendAdjustment

        // ── 季节性因子 ──
        val seasonalFactor = predictions
            .filter { it.seasonalFactor != 1.0 }
            .map { it.seasonalFactor }
            .averageOrNull() ?: 1.0

        // ── 趋势方向 ──
        val trendDirection = detectTrendDirection(clean)

        // ── 最终预测 ──
        val predictedRemaining = finalDailyRate * daysRemaining * seasonalFactor
        val predictedTotal = monthConsumptionSoFar + predictedRemaining

        // ── 置信区间（基于模型方差） ──
        val modelVariance = calculateModelVariance(predictions, weightedDailyRate)
        val confidenceMargin = predictedTotal * modelVariance * 1.96  // 95% 置信区间

        // ── 选择最佳模型名称 ──
        val bestModel = predictions.maxByOrNull { it.confidence }?.model ?: "Ensemble"

        return MonthPrediction(
            dailyRateKwh = finalDailyRate * seasonalFactor,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            consumedSoFarKwh = monthConsumptionSoFar,
            predictedRemainingKwh = predictedRemaining,
            predictedTotalKwh = predictedTotal,
            confidenceLow = (predictedTotal - confidenceMargin).coerceAtLeast(0.0),
            confidenceHigh = predictedTotal + confidenceMargin,
            modelUsed = bestModel,
            seasonalFactor = seasonalFactor,
            trendDirection = trendDirection
        )
    }

    // ════════════════════════════════════════════
    //  模型1: 双重指数平滑 (DES / Holt)
    // ════════════════════════════════════════════

    private fun doubleExponentialSmooth(records: List<Reading>): DesResult {
        val dailyRates = buildDailyRateSeries(records)
        if (dailyRates.size < 3) {
            val simple = dailyRates.average()
            return DesResult(baseLevel = simple, trend = 0.0, trendAdjustedRate = simple)
        }

        var level = dailyRates[0]
        var trend = dailyRates.getOrNull(1)?.minus(dailyRates[0]) ?: 0.0

        for (i in 2 until dailyRates.size) {
            val prevLevel = level
            level = ALPHA * dailyRates[i] + (1 - ALPHA) * (level + trend)
            trend = BETA * (level - prevLevel) + (1 - BETA) * trend
        }

        val trendAdjustedRate = (level + trend * 0.5).coerceAtLeast(0.1)
        return DesResult(baseLevel = level, trend = trend, trendAdjustedRate = trendAdjustedRate)
    }

    // ════════════════════════════════════════════
    //  模型2: Holt-Winters 三重指数平滑（加法季节性）
    // ════════════════════════════════════════════

    private fun holtWintersSmooth(records: List<Reading>): HwResult? {
        val dailyRates = buildDailyRateSeries(records)
        if (dailyRates.size < SEASON_LENGTH * 2) return null

        val n = dailyRates.size
        val seasonLen = SEASON_LENGTH

        // 初始化：第一个季节的平均值作为初始水平
        val firstSeason = dailyRates.take(seasonLen)
        val initialLevel = firstSeason.average()
        val initialTrend = if (n >= seasonLen * 2) {
            val secondSeason = dailyRates.subList(seasonLen, seasonLen * 2)
            (secondSeason.average() - initialLevel) / seasonLen
        } else 0.0

        // 初始化季节因子
        val seasonal = DoubleArray(seasonLen)
        for (i in 0 until seasonLen) {
            seasonal[i] = dailyRates[i] - initialLevel
        }

        // Holt-Winters 迭代
        var level = initialLevel
        var trend = initialTrend
        val seasonalCopy = seasonal.copyOf()

        for (t in seasonLen until n) {
            val s = t % seasonLen
            val prevLevel = level
            level = ALPHA * (dailyRates[t] - seasonalCopy[s]) + (1 - ALPHA) * (level + trend)
            trend = BETA * (level - prevLevel) + (1 - BETA) * trend
            seasonalCopy[s] = GAMMA * (dailyRates[t] - level) + (1 - GAMMA) * seasonalCopy[s]
        }

        // 预测下一天
        val nextSeason = n % seasonLen
        val forecast = level + trend + seasonalCopy[nextSeason]

        // 季节性因子（最强季节 / 平均）
        val maxSeasonal = seasonalCopy.max()
        val avgSeasonal = seasonalCopy.average()
        val seasonalFactor = if (avgSeasonal > 0) (maxSeasonal / avgSeasonal).coerceIn(0.8, 1.3) else 1.0

        return HwResult(
            dailyRate = forecast.coerceAtLeast(0.1),
            seasonalFactor = seasonalFactor
        )
    }

    // ════════════════════════════════════════════
    //  模型3: 线性回归
    // ════════════════════════════════════════════

    private fun linearRegression(records: List<Reading>): LrResult? {
        val dailyRates = buildDailyRateSeries(records)
        if (dailyRates.size < 3) return null

        val n = dailyRates.size
        val x = (0 until n).map { it.toDouble() }
        val y = dailyRates

        val xMean = x.average()
        val yMean = y.average()

        var ssXY = 0.0
        var ssXX = 0.0
        for (i in 0 until n) {
            ssXY += (x[i] - xMean) * (y[i] - yMean)
            ssXX += (x[i] - xMean) * (x[i] - xMean)
        }

        if (ssXX == 0.0) return null

        val slope = ssXY / ssXX
        val intercept = yMean - slope * xMean

        // 预测下一点
        val predictedRate = intercept + slope * n

        // R² 决定系数
        var ssRes = 0.0
        var ssTot = 0.0
        for (i in 0 until n) {
            val predicted = intercept + slope * x[i]
            ssRes += (y[i] - predicted) * (y[i] - predicted)
            ssTot += (y[i] - yMean) * (y[i] - yMean)
        }
        val rSquared = if (ssTot > 0) 1 - ssRes / ssTot else 0.0

        return LrResult(
            dailyRate = predictedRate.coerceAtLeast(0.1),
            slope = slope,
            rSquared = rSquared
        )
    }

    // ════════════════════════════════════════════
    //  模型4: 自回归 (AR)
    // ════════════════════════════════════════════

    private fun autoRegressive(records: List<Reading>): Double? {
        val dailyRates = buildDailyRateSeries(records)
        if (dailyRates.size < AR_LAG + 2) return null

        val n = dailyRates.size
        val lag = AR_LAG.coerceAtMost(n - 1)

        // 简单AR模型：最近lag天的加权平均
        val weights = DoubleArray(lag) { 1.0 / (it + 1) }  // 越近权重越高
        val weightSum = weights.sum()

        var prediction = 0.0
        for (i in 0 until lag) {
            prediction += dailyRates[n - 1 - i] * weights[i]
        }
        prediction /= weightSum

        return prediction.coerceAtLeast(0.1)
    }

    // ════════════════════════════════════════════
    //  辅助函数
    // ════════════════════════════════════════════

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
            // 高温修正（空调耗电）
            val heatMultiplier = when {
                day.tempMax >= 40.0 -> 1.5
                day.tempMax >= 38.0 -> 1.35
                day.tempMax >= HEAT_THRESHOLD -> 1.15
                else -> 1.0
            }
            // 低温修正（取暖耗电）
            val coldMultiplier = when {
                day.tempMin <= 0.0 -> 1.3
                day.tempMin <= COLD_THRESHOLD -> 1.15
                else -> 1.0
            }
            totalDelta += maxOf(heatMultiplier, coldMultiplier) - 1.0
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

    /**
     * 检测趋势方向：基于最近数据的线性回归斜率。
     */
    private fun detectTrendDirection(records: List<Reading>): TrendDirection {
        val dailyRates = buildDailyRateSeries(records)
        if (dailyRates.size < 5) return TrendDirection.STABLE

        val recent = dailyRates.takeLast(10)
        val n = recent.size
        val x = (0 until n).map { it.toDouble() }
        val xMean = x.average()
        val yMean = recent.average()

        var ssXY = 0.0
        var ssXX = 0.0
        for (i in 0 until n) {
            ssXY += (x[i] - xMean) * (recent[i] - yMean)
            ssXX += (x[i] - xMean) * (x[i] - xMean)
        }

        if (ssXX == 0.0) return TrendDirection.STABLE
        val slope = ssXY / ssXX

        return when {
            slope > yMean * 0.05 -> TrendDirection.RISING
            slope < -yMean * 0.05 -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }
    }

    /**
     * 计算模型方差（用于置信区间）。
     */
    private fun calculateModelVariance(predictions: List<ModelPrediction>, mean: Double): Double {
        if (predictions.size < 2) return 0.15  // 默认15%方差

        val variance = predictions.map { (it.dailyRate - mean) * (it.dailyRate - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        return (stdDev / mean).coerceIn(0.05, 0.30)  // 5%-30%
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
        val confidenceMargin = predictedTotal * 0.20  // 简单模型20%置信区间

        return MonthPrediction(
            dailyRateKwh = dailyRate,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            consumedSoFarKwh = monthConsumptionSoFar,
            predictedRemainingKwh = predictedRemaining,
            predictedTotalKwh = predictedTotal,
            confidenceLow = (predictedTotal - confidenceMargin).coerceAtLeast(0.0),
            confidenceHigh = predictedTotal + confidenceMargin,
            modelUsed = "Simple Average"
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

    // ── 内部数据类 ──

    private data class DesResult(
        val baseLevel: Double,
        val trend: Double,
        val trendAdjustedRate: Double
    )

    private data class HwResult(
        val dailyRate: Double,
        val seasonalFactor: Double
    )

    private data class LrResult(
        val dailyRate: Double,
        val slope: Double,
        val rSquared: Double
    )

    private data class ModelPrediction(
        val model: String,
        val dailyRate: Double,
        val confidence: Double,
        val seasonalFactor: Double = 1.0
    )

    // ── 日期工具 ──

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

    private fun List<Double>.averageOrNull(): Double? {
        return if (isEmpty()) null else average()
    }
}
