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
        private val THEME_DARK = booleanPreferencesKey("theme_dark")
        private val THEME_FOLLOW_SYSTEM = booleanPreferencesKey("theme_follow_system")
        private val PEAK_PRICE = doublePreferencesKey("peak_price")
        private val VALLEY_PRICE = doublePreferencesKey("valley_price")
        private val FLAT_PRICE = doublePreferencesKey("flat_price")
        private val WATER_PRICE = doublePreferencesKey("water_price")
        private val WATER_TIER_1_LIMIT = doublePreferencesKey("water_tier_1_limit")
        private val WATER_TIER_2_LIMIT = doublePreferencesKey("water_tier_2_limit")
        private val WATER_TIER_2_PRICE = doublePreferencesKey("water_tier_2_price")
        private val WATER_TIER_3_PRICE = doublePreferencesKey("water_tier_3_price")
        private val CHART_SHOW_COST = booleanPreferencesKey("chart_show_cost")
        private val PEAK_VALLEY_EXPANDED = booleanPreferencesKey("peak_valley_expanded")
        private val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
        private val WEATHER_CITY_ID = stringPreferencesKey("weather_city_id")
        private val TH_TOTAL_ELECTRIC_MIN = doublePreferencesKey("th_total_electric_min")
        private val TH_PEAK_MIN = doublePreferencesKey("th_peak_min")
        private val TH_PEAK_MAX = doublePreferencesKey("th_peak_max")
        private val TH_VALLEY_MIN = doublePreferencesKey("th_valley_min")
        private val TH_VALLEY_MAX = doublePreferencesKey("th_valley_max")
        private val TH_WATER_MAX = doublePreferencesKey("th_water_max")
    }

    val isDarkTheme: Flow<Boolean> = dataStore.data.map { it[THEME_DARK] ?: true }
    val followSystemTheme: Flow<Boolean> = dataStore.data.map { it[THEME_FOLLOW_SYSTEM] ?: false }
    suspend fun setTheme(dark: Boolean, followSystem: Boolean) = dataStore.edit {
        it[THEME_DARK] = dark
        it[THEME_FOLLOW_SYSTEM] = followSystem
    }

    val billingRules: Flow<BillingRules> = dataStore.data.map { prefs ->
        BillingRules(
            peakPrice = prefs[PEAK_PRICE] ?: 0.6,
            valleyPrice = prefs[VALLEY_PRICE] ?: 0.3,
            flatPrice = prefs[FLAT_PRICE] ?: 0.5,
            waterTier1Limit = prefs[WATER_TIER_1_LIMIT] ?: 15.0,
            waterTier2Limit = prefs[WATER_TIER_2_LIMIT] ?: 25.0,
            waterTier1Price = prefs[WATER_PRICE] ?: 3.5,
            waterTier2Price = prefs[WATER_TIER_2_PRICE] ?: 4.5,
            waterTier3Price = prefs[WATER_TIER_3_PRICE] ?: 6.0
        )
    }
    val peakPrice: Flow<Double> = billingRules.map { it.peakPrice }
    val valleyPrice: Flow<Double> = billingRules.map { it.valleyPrice }
    val flatPrice: Flow<Double> = billingRules.map { it.flatPrice }
    val waterPrice: Flow<Double> = billingRules.map { it.waterTier1Price }

    suspend fun setBillingRules(rules: BillingRules) = dataStore.edit {
        it[PEAK_PRICE] = rules.peakPrice
        it[VALLEY_PRICE] = rules.valleyPrice
        it[FLAT_PRICE] = rules.flatPrice
        it[WATER_TIER_1_LIMIT] = rules.waterTier1Limit
        it[WATER_TIER_2_LIMIT] = rules.waterTier2Limit
        it[WATER_PRICE] = rules.waterTier1Price
        it[WATER_TIER_2_PRICE] = rules.waterTier2Price
        it[WATER_TIER_3_PRICE] = rules.waterTier3Price
    }

    /** Kept for callers compiled against the first settings screen. */
    suspend fun setPricing(peak: Double, valley: Double, flat: Double, water: Double) {
        val current = billingRules.first()
        setBillingRules(current.copy(peakPrice = peak, valleyPrice = valley, flatPrice = flat, waterTier1Price = water))
    }

    val chartShowCost: Flow<Boolean> = dataStore.data.map { it[CHART_SHOW_COST] ?: false }
    val peakValleyExpanded: Flow<Boolean> = dataStore.data.map { it[PEAK_VALLEY_EXPANDED] ?: false }
    suspend fun setChartShowCost(showCost: Boolean) = dataStore.edit { it[CHART_SHOW_COST] = showCost }
    suspend fun setPeakValleyExpanded(expanded: Boolean) = dataStore.edit { it[PEAK_VALLEY_EXPANDED] = expanded }

    val weatherApiKey: Flow<String> = dataStore.data.map { it[WEATHER_API_KEY] ?: "" }
    val weatherCityId: Flow<String> = dataStore.data.map { it[WEATHER_CITY_ID] ?: "101010100" }
    suspend fun setWeatherConfig(apiKey: String, cityId: String) = dataStore.edit {
        it[WEATHER_API_KEY] = apiKey.trim()
        it[WEATHER_CITY_ID] = cityId.trim()
    }

    suspend fun getCachedThresholds(): ClassificationThresholds? = dataStore.data.first().let { prefs ->
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

    suspend fun cacheThresholds(t: ClassificationThresholds) = dataStore.edit {
        it[TH_TOTAL_ELECTRIC_MIN] = t.totalElectricMin
        it[TH_PEAK_MIN] = t.peakMin
        it[TH_PEAK_MAX] = t.peakMax
        it[TH_VALLEY_MIN] = t.valleyMin
        it[TH_VALLEY_MAX] = t.valleyMax
        it[TH_WATER_MAX] = t.waterMax
    }
}
