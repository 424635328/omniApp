package com.example.energyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillingSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val peakPriceFlow: Flow<Double> = userPreferences.peakPrice
    val valleyPriceFlow: Flow<Double> = userPreferences.valleyPrice
    val flatPriceFlow: Flow<Double> = userPreferences.flatPrice
    val waterPriceFlow: Flow<Double> = userPreferences.waterPrice

    private var peakPrice = 0.6
    private var valleyPrice = 0.3
    private var flatPrice = 0.5
    private var waterPrice = 3.5

    fun updatePeakPrice(value: Double) { peakPrice = value }
    fun updateValleyPrice(value: Double) { valleyPrice = value }
    fun updateFlatPrice(value: Double) { flatPrice = value }
    fun updateWaterPrice(value: Double) { waterPrice = value }

    fun saveAll() {
        viewModelScope.launch {
            userPreferences.setPricing(peakPrice, valleyPrice, flatPrice, waterPrice)
        }
    }
}
