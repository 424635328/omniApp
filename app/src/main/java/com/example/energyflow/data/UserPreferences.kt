package com.example.energyflow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // Theme
        private val THEME_DARK = booleanPreferencesKey("theme_dark")
        private val THEME_FOLLOW_SYSTEM = booleanPreferencesKey("theme_follow_system")

        // Pricing: peak (元/度), valley (元/度), water (元/吨)
        private val PEAK_PRICE = doublePreferencesKey("peak_price")
        private val VALLEY_PRICE = doublePreferencesKey("valley_price")
        private val FLAT_PRICE = doublePreferencesKey("flat_price")
        private val WATER_PRICE = doublePreferencesKey("water_price")

        // Chart
        private val CHART_SHOW_COST = booleanPreferencesKey("chart_show_cost")
        private val PEAK_VALLEY_EXPANDED = booleanPreferencesKey("peak_valley_expanded")

        // Weather
        private val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
        private val WEATHER_CITY_ID = stringPreferencesKey("weather_city_id")

        // Classification thresholds cache
        private val TH_TOTAL_ELECTRIC_MIN = doublePreferencesKey("th_total_electric_min")
        private val TH_PEAK_MIN = doublePreferencesKey("th_peak_min")
        private val TH_PEAK_MAX = doublePreferencesKey("th_peak_max")
        private val TH_VALLEY_MIN = doublePreferencesKey("th_valley_min")
        private val TH_VALLEY_MAX = doublePreferencesKey("th_valley_max")
        private val TH_WATER_MAX = doublePreferencesKey("th_water_max")
    }

    // ─── Theme ──────────────────────────────────────────────
    val isDarkTheme: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[THEME_DARK] ?: true
    }

    val followSystemTheme: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[THEME_FOLLOW_SYSTEM] ?: false
    }

    suspend fun setTheme(dark: Boolean, followSystem: Boolean) {
        dataStore.edit {
            it[THEME_DARK] = dark
            it[THEME_FOLLOW_SYSTEM] = followSystem
        }
    }

    // ─── Pricing ────────────────────────────────────────────
    val peakPrice: Flow<Double> = dataStore.data.map { prefs ->
        prefs[PEAK_PRICE] ?: 0.6
    }

    val valleyPrice: Flow<Double> = dataStore.data.map { prefs ->
        prefs[VALLEY_PRICE] ?: 0.3
    }

    val flatPrice: Flow<Double> = dataStore.data.map { prefs ->
        prefs[FLAT_PRICE] ?: 0.5
    }

    val waterPrice: Flow<Double> = dataStore.data.map { prefs ->
        prefs[WATER_PRICE] ?: 3.5
    }

    suspend fun setPricing(
        peak: Double,
        valley: Double,
        flat: Double,
        water: Double
    ) {
        dataStore.edit {
            it[PEAK_PRICE] = peak
            it[VALLEY_PRICE] = valley
            it[FLAT_PRICE] = flat
            it[WATER_PRICE] = water
        }
    }

    // ─── Chart ──────────────────────────────────────────────
    val chartShowCost: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[CHART_SHOW_COST] ?: false
    }

    val peakValleyExpanded: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PEAK_VALLEY_EXPANDED] ?: false
    }

    suspend fun setChartShowCost(showCost: Boolean) {
        dataStore.edit { it[CHART_SHOW_COST] = showCost }
    }

    suspend fun setPeakValleyExpanded(expanded: Boolean) {
        dataStore.edit { it[PEAK_VALLEY_EXPANDED] = expanded }
    }

    // ─── Weather ──────────────────────────────────────────

    val weatherApiKey: Flow<String> = dataStore.data.map { prefs ->
        prefs[WEATHER_API_KEY] ?: ""
    }

    val weatherCityId: Flow<String> = dataStore.data.map { prefs ->
        prefs[WEATHER_CITY_ID] ?: "101010100"
    }

    suspend fun setWeatherConfig(apiKey: String, cityId: String) {
        dataStore.edit {
            it[WEATHER_API_KEY] = apiKey
            it[WEATHER_CITY_ID] = cityId
        }
    }

    // ─── Classification Thresholds ──────────────────────────
    suspend fun getCachedThresholds(): ClassificationThresholds? {
        return dataStore.data.first().let { prefs ->
            val total = prefs[TH_TOTAL_ELECTRIC_MIN] ?: return@let null
            ClassificationThresholds(
                totalElectricMin = total,
                peakMin = prefs[TH_PEAK_MIN] ?: return@let null,
                peakMax = prefs[TH_PEAK_MAX] ?: return@let null,
                valleyMin = prefs[TH_VALLEY_MIN] ?: return@let null,
                valleyMax = prefs[TH_VALLEY_MAX] ?: return@let null,
                waterMax = prefs[TH_WATER_MAX] ?: return@let null
            )
        }
    }

    suspend fun cacheThresholds(t: ClassificationThresholds) {
        dataStore.edit {
            it[TH_TOTAL_ELECTRIC_MIN] = t.totalElectricMin
            it[TH_PEAK_MIN] = t.peakMin
            it[TH_PEAK_MAX] = t.peakMax
            it[TH_VALLEY_MIN] = t.valleyMin
            it[TH_VALLEY_MAX] = t.valleyMax
            it[TH_WATER_MAX] = t.waterMax
        }
    }
}
