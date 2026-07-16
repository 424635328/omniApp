package com.example.energyflow.data

import com.example.energyflow.shared.PredictiveAnalyzerShared
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt 包装的预测分析器 — 委托给 KMP 共享模块 [PredictiveAnalyzerShared]。
 */
@Singleton
class PredictiveAnalyzer @Inject constructor() {

    fun predictMonth(
        records: List<MeterRecord>,
        weatherForecast: List<DailyWeather> = emptyList(),
        now: java.time.LocalDateTime = java.time.LocalDateTime.now()
    ): MonthPrediction? {
        val ktNow = now.toKtDateTime()
        val readings = records.map { it.toSharedReading() }
        val forecast = weatherForecast.map { it.toSharedForecast() }

        val result = PredictiveAnalyzerShared.predictMonth(
            records = readings,
            weatherForecast = forecast,
            now = ktNow
        ) ?: return null

        return MonthPrediction(
            dailyRateKwh = result.dailyRateKwh,
            daysElapsed = result.daysElapsed,
            daysRemaining = result.daysRemaining,
            consumedSoFarKwh = result.consumedSoFarKwh,
            predictedRemainingKwh = result.predictedRemainingKwh,
            predictedTotalKwh = result.predictedTotalKwh
        )
    }
}

private fun java.time.LocalDateTime.toKtDateTime(): kotlinx.datetime.LocalDateTime =
    kotlinx.datetime.LocalDateTime(year, monthValue, dayOfMonth, hour, minute, second, nano)

private fun MeterRecord.toSharedReading(): PredictiveAnalyzerShared.Reading =
    PredictiveAnalyzerShared.Reading(timestamp.toKtDateTime(), electricTotal)

private fun DailyWeather.toSharedForecast(): PredictiveAnalyzerShared.WeatherForecast =
    PredictiveAnalyzerShared.WeatherForecast(
        kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth), tempMax
    )
