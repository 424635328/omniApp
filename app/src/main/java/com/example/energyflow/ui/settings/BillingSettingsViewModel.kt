package com.example.energyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.BatchInsertResult
import com.example.energyflow.data.BillingRules
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Locale

@HiltViewModel
class BillingSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val repository: MeterRepository
) : ViewModel() {
    val billingRulesFlow: Flow<BillingRules> = userPreferences.billingRules
    val deepSeekApiKeyFlow: Flow<String> = userPreferences.deepSeekApiKey
    val isDarkThemeFlow: Flow<Boolean> = userPreferences.isDarkTheme
    val followSystemThemeFlow: Flow<Boolean> = userPreferences.followSystemTheme
    val peakValleyExpandedFlow: Flow<Boolean> = userPreferences.peakValleyExpanded
    val themeDistEnabledFlow: Flow<Boolean> = userPreferences.themeDistEnabled

    private var draft = BillingRules()

    init {
        viewModelScope.launch {
            billingRulesFlow.collect { draft = it }
        }
    }

    // ── 电价 ──
    fun updatePeakPrice(value: Double) { draft = draft.copy(peakPrice = value) }
    fun updateValleyPrice(value: Double) { draft = draft.copy(valleyPrice = value) }
    fun updateFlatPrice(value: Double) { draft = draft.copy(flatPrice = value) }

    // ── 用电阶梯 ──
    fun updateElecTier1Limit(value: Double) { draft = draft.copy(electricTier1Limit = value) }
    fun updateElecTier2Limit(value: Double) { draft = draft.copy(electricTier2Limit = value) }
    fun updateElecTier2Surcharge(value: Double) { draft = draft.copy(electricTier2Surcharge = value) }
    fun updateElecTier3Surcharge(value: Double) { draft = draft.copy(electricTier3Surcharge = value) }

    // ── 水价阶梯 ──
    fun updateWaterTier1Limit(value: Double) { draft = draft.copy(waterTier1Limit = value) }
    fun updateWaterTier2Limit(value: Double) { draft = draft.copy(waterTier2Limit = value) }
    fun updateWaterTier1Price(value: Double) { draft = draft.copy(waterTier1Price = value) }
    fun updateWaterTier2Price(value: Double) { draft = draft.copy(waterTier2Price = value) }
    fun updateWaterTier3Price(value: Double) { draft = draft.copy(waterTier3Price = value) }

    fun saveBillingRules() = viewModelScope.launch { userPreferences.setBillingRules(draft) }
    fun setTheme(dark: Boolean, followSystem: Boolean) = viewModelScope.launch {
        userPreferences.setTheme(dark, followSystem)
    }
    fun setPeakValleyExpanded(expanded: Boolean) = viewModelScope.launch {
        userPreferences.setPeakValleyExpanded(expanded)
    }
    fun saveDeepSeekApiKey(key: String) = viewModelScope.launch {
        userPreferences.setDeepSeekApiKey(key)
    }
    fun setThemeDistEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferences.setThemeDistEnabled(enabled)
    }

    // ── 数据管理 ────────────────────────────────────────────

    fun deleteAllRecords() = viewModelScope.launch {
        repository.deleteAll()
    }

    suspend fun exportRecordsToText(): String {
        val records = repository.getAllRecords().first()
        return buildExportText(records)
    }

    suspend fun importRecordsFromText(text: String): String {
        return when (val result = repository.batchInsert(text)) {
            is BatchInsertResult.Success ->
                "成功导入 ${result.count} 条记录"
            is BatchInsertResult.Warning ->
                "导入被拦截：${result.warnings.firstOrNull() ?: "数据异常"}"
            is BatchInsertResult.PartialSuccess ->
                "成功导入 ${result.successCount} 条，${result.errors.size} 条失败"
        }
    }

    private fun buildExportText(records: List<MeterRecord>): String {
        if (records.isEmpty()) return ""

        val sb = StringBuilder()
        val sorted = records.sortedBy { it.timestamp }
        var lastDateStr = ""

        for (record in sorted) {
            val ts = record.timestamp
            val dateStr = "${ts.monthValue}.${ts.dayOfMonth}"
            val timeStr = "${ts.hour.toString().padStart(2, '0')}.${ts.minute.toString().padStart(2, '0')}"

            if (dateStr != lastDateStr) {
                sb.appendLine(dateStr)
                lastDateStr = dateStr
            }

            val hasElec = record.isElectricRecorded && record.electricTotal != null
            val hasWater = record.isWaterRecorded && record.waterTotal != null
            val hasNote = !record.note.isNullOrBlank()

            when {
                hasElec && hasWater -> {
                    sb.append("$timeStr ${formatExport(record.electricTotal!!)} ${formatExport(record.waterTotal!!)}")
                    if (hasNote) sb.append(" ${record.note}")
                    sb.appendLine()
                }
                hasElec -> {
                    sb.append("$timeStr ${formatExport(record.electricTotal!!)}")
                    if (hasNote) sb.append(" ${record.note}")
                    sb.appendLine()
                }
                hasWater -> {
                    sb.appendLine("水${formatExport(record.waterTotal!!)}")
                }
                hasNote -> {
                    sb.appendLine("$timeStr ${record.note}")
                }
            }
        }

        return sb.toString()
    }

    private fun formatExport(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }
}
