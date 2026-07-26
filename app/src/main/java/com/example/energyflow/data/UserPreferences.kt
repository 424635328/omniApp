package com.example.energyflow.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val credentialStore: DeepSeekCredentialStore? = null,
) {
    companion object {
        // ── 计费规则版本号（递增即触发迁移，重置为南京最新默认值） ──
        private const val CURRENT_BILLING_VERSION = 3
        private val BILLING_VERSION = intPreferencesKey("billing_version")

        private val THEME_DARK = booleanPreferencesKey("theme_dark")
        private val THEME_FOLLOW_SYSTEM = booleanPreferencesKey("theme_follow_system")
        private val PEAK_PRICE = doublePreferencesKey("peak_price")
        private val VALLEY_PRICE = doublePreferencesKey("valley_price")
        private val FLAT_PRICE = doublePreferencesKey("flat_price")

        // 用电阶梯
        private val ELEC_TIER1_LIMIT = doublePreferencesKey("elec_tier1_limit")
        private val ELEC_TIER2_LIMIT = doublePreferencesKey("elec_tier2_limit")
        private val ELEC_TIER2_SURCHARGE = doublePreferencesKey("elec_tier2_surcharge")
        private val ELEC_TIER3_SURCHARGE = doublePreferencesKey("elec_tier3_surcharge")

        // 用水阶梯
        private val WATER_PRICE = doublePreferencesKey("water_price")
        private val WATER_TIER_1_LIMIT = doublePreferencesKey("water_tier_1_limit")
        private val WATER_TIER_2_LIMIT = doublePreferencesKey("water_tier_2_limit")
        private val WATER_TIER_2_PRICE = doublePreferencesKey("water_tier_2_price")
        private val WATER_TIER_3_PRICE = doublePreferencesKey("water_tier_3_price")

        private val CHART_SHOW_COST = booleanPreferencesKey("chart_show_cost")
        private val PEAK_VALLEY_EXPANDED = booleanPreferencesKey("peak_valley_expanded")
        private val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val TH_TOTAL_ELECTRIC_MIN = doublePreferencesKey("th_total_electric_min")
        private val TH_PEAK_MIN = doublePreferencesKey("th_peak_min")
        private val TH_PEAK_MAX = doublePreferencesKey("th_peak_max")
        private val TH_VALLEY_MIN = doublePreferencesKey("th_valley_min")
        private val TH_VALLEY_MAX = doublePreferencesKey("th_valley_max")
        private val TH_WATER_MAX = doublePreferencesKey("th_water_max")
        private val THEME_CACHE = stringPreferencesKey("theme_cache_json")
        private val THEME_DIST_ENABLED = booleanPreferencesKey("theme_dist_enabled")
        private val PREDICTION_SNAPSHOT = stringPreferencesKey("prediction_snapshot_json")
        private val WEATHER_FORECAST_CACHE = stringPreferencesKey("weather_forecast_cache")
        private val WEATHER_FORECAST_DATE = stringPreferencesKey("weather_forecast_date")
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val FILTER_DUPLICATES = booleanPreferencesKey("filter_duplicates")

        // ── 碳足迹因子 ──
        private val CARBON_ELECTRIC_FACTOR = doublePreferencesKey("carbon_electric_factor")
        private val CARBON_GAS_FACTOR = doublePreferencesKey("carbon_gas_factor")
        private val CARBON_TREE_KG_PER_YEAR = doublePreferencesKey("carbon_tree_kg_per_year")
        private val GAS_UNIT_PRICE = doublePreferencesKey("gas_unit_price")
    }

    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val isFilterDuplicates: Flow<Boolean> = dataStore.data.map { it[FILTER_DUPLICATES] ?: false }

    suspend fun setFilterDuplicates(enabled: Boolean) {
        dataStore.edit { it[FILTER_DUPLICATES] = enabled }
    }

    suspend fun completeOnboarding() {
        dataStore.edit { it[ONBOARDING_COMPLETE] = true }
    }

    val isDarkTheme: Flow<Boolean> = dataStore.data.map { it[THEME_DARK] ?: true }
    val followSystemTheme: Flow<Boolean> = dataStore.data.map { it[THEME_FOLLOW_SYSTEM] ?: false }
    suspend fun setTheme(dark: Boolean, followSystem: Boolean) = dataStore.edit {
        it[THEME_DARK] = dark
        it[THEME_FOLLOW_SYSTEM] = followSystem
    }

    /**
     * 计费规则 — 带版本迁移。
     *
     * 首次启动或版本号低于 CURRENT_BILLING_VERSION 时，
     * 自动重置为南京建邺区 2026 年最新默认值 **并写入 DataStore**，
     * 确保迁移只触发一次，避免 Compose 重组引发循环。
     */
    val billingRules: Flow<BillingRules> = flow {
        val initial = dataStore.data.first()
        val savedVersion = initial[BILLING_VERSION] ?: 0

        if (savedVersion < CURRENT_BILLING_VERSION) {
            Log.i("UserPreferences", "Billing rules migration v$savedVersion → v$CURRENT_BILLING_VERSION — using Nanjing defaults")
            // ═══ 写入版本号 + 默认值，确保后继不再触发迁移 ═══
            dataStore.edit {
                it[BILLING_VERSION] = CURRENT_BILLING_VERSION
                val defaults = BillingRules()
                it[PEAK_PRICE] = defaults.peakPrice
                it[VALLEY_PRICE] = defaults.valleyPrice
                it[FLAT_PRICE] = defaults.flatPrice
                it[ELEC_TIER1_LIMIT] = defaults.electricTier1Limit
                it[ELEC_TIER2_LIMIT] = defaults.electricTier2Limit
                it[ELEC_TIER2_SURCHARGE] = defaults.electricTier2Surcharge
                it[ELEC_TIER3_SURCHARGE] = defaults.electricTier3Surcharge
                it[WATER_TIER_1_LIMIT] = defaults.waterTier1Limit
                it[WATER_TIER_2_LIMIT] = defaults.waterTier2Limit
                it[WATER_PRICE] = defaults.waterTier1Price
                it[WATER_TIER_2_PRICE] = defaults.waterTier2Price
                it[WATER_TIER_3_PRICE] = defaults.waterTier3Price
                it[GAS_UNIT_PRICE] = defaults.gasUnitPrice
            }
            emit(BillingRules())
        }

        // 后继变更（含迁移写回后触发的新 emit）统一通过 reactive 订阅
        emitAll(dataStore.data.map { prefs -> collectBillingRules(prefs) })
    }

    /** 从 Preferences 读取计费规则（假设版本已是最新） */
    private fun collectBillingRules(prefs: Preferences): BillingRules {
        return BillingRules(
            peakPrice = prefs[PEAK_PRICE] ?: 0.5583,
            valleyPrice = prefs[VALLEY_PRICE] ?: 0.3583,
            flatPrice = prefs[FLAT_PRICE] ?: 0.5283,
            electricTier1Limit = prefs[ELEC_TIER1_LIMIT] ?: 230.0,
            electricTier2Limit = prefs[ELEC_TIER2_LIMIT] ?: 400.0,
            electricTier2Surcharge = prefs[ELEC_TIER2_SURCHARGE] ?: 0.05,
            electricTier3Surcharge = prefs[ELEC_TIER3_SURCHARGE] ?: 0.30,
            waterTier1Limit = prefs[WATER_TIER_1_LIMIT] ?: 16.67,
            waterTier2Limit = prefs[WATER_TIER_2_LIMIT] ?: 22.5,
            waterTier1Price = prefs[WATER_PRICE] ?: 3.42,
            waterTier2Price = prefs[WATER_TIER_2_PRICE] ?: 4.32,
            waterTier3Price = prefs[WATER_TIER_3_PRICE] ?: 7.02,
            gasUnitPrice = prefs[GAS_UNIT_PRICE] ?: 2.8
        )
    }
    val peakPrice: Flow<Double> = billingRules.map { it.peakPrice }
    val valleyPrice: Flow<Double> = billingRules.map { it.valleyPrice }
    val flatPrice: Flow<Double> = billingRules.map { it.flatPrice }
    val waterPrice: Flow<Double> = billingRules.map { it.waterTier1Price }

    suspend fun setBillingRules(rules: BillingRules) = dataStore.edit {
        it[BILLING_VERSION] = CURRENT_BILLING_VERSION
        it[PEAK_PRICE] = rules.peakPrice
        it[VALLEY_PRICE] = rules.valleyPrice
        it[FLAT_PRICE] = rules.flatPrice
        it[ELEC_TIER1_LIMIT] = rules.electricTier1Limit
        it[ELEC_TIER2_LIMIT] = rules.electricTier2Limit
        it[ELEC_TIER2_SURCHARGE] = rules.electricTier2Surcharge
        it[ELEC_TIER3_SURCHARGE] = rules.electricTier3Surcharge
        it[WATER_TIER_1_LIMIT] = rules.waterTier1Limit
        it[WATER_TIER_2_LIMIT] = rules.waterTier2Limit
        it[WATER_PRICE] = rules.waterTier1Price
        it[WATER_TIER_2_PRICE] = rules.waterTier2Price
        it[WATER_TIER_3_PRICE] = rules.waterTier3Price
        it[GAS_UNIT_PRICE] = rules.gasUnitPrice
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

    val deepSeekApiKey: Flow<String> = credentialStore?.apiKey
        ?: dataStore.data.map { it[DEEPSEEK_API_KEY] ?: "" }

    suspend fun setDeepSeekApiKey(key: String) {
        credentialStore?.setApiKey(key) ?: dataStore.edit { it[DEEPSEEK_API_KEY] = key.trim() }
        if (credentialStore != null) {
            dataStore.edit { it.remove(DEEPSEEK_API_KEY) }
        }
    }

    suspend fun migrateLegacyDeepSeekApiKey() {
        val secureStore = credentialStore ?: return
        val legacyKey = dataStore.data.first()[DEEPSEEK_API_KEY]?.trim().orEmpty()
        if (legacyKey.isNotEmpty() && secureStore.apiKey.value.isEmpty()) {
            secureStore.setApiKey(legacyKey)
        }
        if (legacyKey.isNotEmpty()) {
            dataStore.edit { it.remove(DEEPSEEK_API_KEY) }
        }
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

    // ── 每日主题缓存 ────────────────────────────────────────
    val cachedThemeJson: Flow<String?> = dataStore.data.map { it[THEME_CACHE] }
    suspend fun cacheThemeJson(json: String) = dataStore.edit { it[THEME_CACHE] = json }

    // ── 主题应用开关 ──────────────────────────────────────────
    val themeDistEnabled: Flow<Boolean> = dataStore.data.map { it[THEME_DIST_ENABLED] ?: true }
    suspend fun setThemeDistEnabled(enabled: Boolean) = dataStore.edit { it[THEME_DIST_ENABLED] = enabled }

    // ── 月度预测快照 ──────────────────────────────────────────
    val predictionSnapshot: Flow<String?> = dataStore.data.map { it[PREDICTION_SNAPSHOT] }
    suspend fun savePredictionSnapshot(json: String) = dataStore.edit { it[PREDICTION_SNAPSHOT] = json }

    // ── 天气预报缓存 ──────────────────────────────────────────
    val weatherForecastCache: Flow<String?> = dataStore.data.map { it[WEATHER_FORECAST_CACHE] }
    val weatherForecastDate: Flow<String?> = dataStore.data.map { it[WEATHER_FORECAST_DATE] }
    suspend fun cacheWeatherForecast(json: String, date: String) = dataStore.edit {
        it[WEATHER_FORECAST_CACHE] = json
        it[WEATHER_FORECAST_DATE] = date
    }

    // ── 碳足迹因子 ───────────────────────────────────────────

    val carbonElectricFactor: Flow<Double> = dataStore.data.map { it[CARBON_ELECTRIC_FACTOR] ?: 0.583 }
    val carbonGasFactor: Flow<Double> = dataStore.data.map { it[CARBON_GAS_FACTOR] ?: 2.02 }
    val carbonTreeKgPerYear: Flow<Double> = dataStore.data.map { it[CARBON_TREE_KG_PER_YEAR] ?: 20.0 }

    suspend fun setCarbonElectricFactor(value: Double) = dataStore.edit {
        it[CARBON_ELECTRIC_FACTOR] = value
    }

    suspend fun setCarbonGasFactor(value: Double) = dataStore.edit {
        it[CARBON_GAS_FACTOR] = value
    }

    suspend fun setCarbonTreeKgPerYear(value: Double) = dataStore.edit {
        it[CARBON_TREE_KG_PER_YEAR] = value
    }
}
