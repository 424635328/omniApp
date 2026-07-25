package com.example.energyflow.ui

import java.time.YearMonth

data class WrappedState(
    val yearMonth: YearMonth,
    val totalKwh: Double,
    val totalCost: Double,
    val totalCo2Kg: Double,
    val treeDays: Int,
    val badges: List<String>,
    val peakKwh: Double,
    val valleyKwh: Double,
    val flatKwh: Double,
    val hotDays: Int,
    val coldDays: Int,
    val avgTemp: Double?,
    val eventHighlights: List<String>,
    val previousPeriodKwh: Double?,
    val previousPeriodCost: Double?,
    val tips: List<String>,
    val recordCount: Int,
    val isLoading: Boolean = false
)
