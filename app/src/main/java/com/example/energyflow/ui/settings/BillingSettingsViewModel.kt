package com.example.energyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.BillingRules
import com.example.energyflow.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillingSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {
    val billingRulesFlow: Flow<BillingRules> = userPreferences.billingRules
    val isDarkThemeFlow: Flow<Boolean> = userPreferences.isDarkTheme
    val followSystemThemeFlow: Flow<Boolean> = userPreferences.followSystemTheme
    val weatherApiKeyFlow: Flow<String> = userPreferences.weatherApiKey
    val weatherCityIdFlow: Flow<String> = userPreferences.weatherCityId
    val peakValleyExpandedFlow: Flow<Boolean> = userPreferences.peakValleyExpanded

    private var draft = BillingRules()

    init {
        viewModelScope.launch {
            billingRulesFlow.collect { draft = it }
        }
    }

    fun updatePeakPrice(value: Double) { draft = draft.copy(peakPrice = value) }
    fun updateValleyPrice(value: Double) { draft = draft.copy(valleyPrice = value) }
    fun updateFlatPrice(value: Double) { draft = draft.copy(flatPrice = value) }
    fun updateWaterTier1Limit(value: Double) { draft = draft.copy(waterTier1Limit = value) }
    fun updateWaterTier2Limit(value: Double) { draft = draft.copy(waterTier2Limit = value) }
    fun updateWaterTier1Price(value: Double) { draft = draft.copy(waterTier1Price = value) }
    fun updateWaterTier2Price(value: Double) { draft = draft.copy(waterTier2Price = value) }
    fun updateWaterTier3Price(value: Double) { draft = draft.copy(waterTier3Price = value) }

    fun saveBillingRules() = viewModelScope.launch { userPreferences.setBillingRules(draft) }
    fun setTheme(dark: Boolean, followSystem: Boolean) = viewModelScope.launch {
        userPreferences.setTheme(dark, followSystem)
    }
    fun setWeatherConfig(apiKey: String, cityId: String) = viewModelScope.launch {
        userPreferences.setWeatherConfig(apiKey, cityId)
    }
    fun setPeakValleyExpanded(expanded: Boolean) = viewModelScope.launch {
        userPreferences.setPeakValleyExpanded(expanded)
    }
}
