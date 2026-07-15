package com.example.energyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.BillReportGenerator
import com.example.energyflow.data.BatchInsertResult
import com.example.energyflow.data.BillingRules
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import java.util.Locale

@HiltViewModel
class BillingSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val repository: MeterRepository,
    private val costEngine: com.example.energyflow.data.CostEngine
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

    // ── 计费规则模板 JSON 导入/导出 ─────────────────────────

    suspend fun exportRulesToJson(): String {
        val rules = billingRulesFlow.first()
        return Json { prettyPrint = true }.encodeToString(rules)
    }

    suspend fun importRulesFromJson(json: String): String {
        return try {
            val rules = Json { ignoreUnknownKeys = true }.decodeFromString<BillingRules>(json)
            userPreferences.setBillingRules(rules)
            "计费规则模板导入成功"
        } catch (e: Exception) {
            "导入失败: ${e.message}"
        }
    }

    suspend fun generateShareReport(): String? {
        val electricRecords = repository.getElectricRecords().first()
        val waterRecords = repository.getWaterRecords().first()
        val gasRecords = repository.getAllRecords().first().filter { it.isGasRecorded }
        val notesRecords = repository.getRecordsWithNotes().first()
        val data = BillReportGenerator.buildReportData(
            electricRecords, waterRecords, gasRecords, notesRecords, costEngine
        ) ?: return null
        return BillReportGenerator.generateTextReport(data)
    }

    suspend fun exportRecordsToText(): String {
        val records = repository.getAllRecords().first()
        return buildExportText(deduplicateRecords(records))
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

    /** 去掉连续读数相同或同时间戳重复的记录（保留最新一条） */
    private fun deduplicateRecords(records: List<MeterRecord>): List<MeterRecord> {
        val sorted = records.sortedWith(compareByDescending<MeterRecord> { it.timestamp }.thenByDescending { it.id })
        return sorted.filterIndexed { index, record ->
            val prev = sorted.getOrNull(index - 1) ?: return@filterIndexed true
            val hasAnyReading = record.isElectricRecorded || record.isWaterRecorded || record.isGasRecorded
            if (!hasAnyReading) return@filterIndexed true
            // 同时间戳 + 同读数 → 去重
            if (record.timestamp == prev.timestamp) return@filterIndexed false
            val eps = 0.1
            fun same(d1: Double?, d2: Double?): Boolean = when {
                d1 == null && d2 == null -> true
                d1 == null || d2 == null -> false
                else -> kotlin.math.abs(d1 - d2) < eps
            }
            val elecSame = same(record.electricTotal, prev.electricTotal)
            val waterSame = same(record.waterTotal, prev.waterTotal)
            val gasSame = same(record.gasTotal, prev.gasTotal)
            !(elecSame && waterSame && gasSame)
        }
    }

    private fun buildExportText(records: List<MeterRecord>): String {
        if (records.isEmpty()) return ""

        val sb = StringBuilder()
        val sorted = records.sortedBy { it.timestamp }
        var lastDateStr = ""
        var totalPeak = 0.0
        var totalValley = 0.0
        var totalKwh = 0.0

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
            val isRecharge = hasNote && record.note.startsWith("充值")

            when {
                isRecharge -> {
                    sb.appendLine("# ${record.note}")
                    continue
                }
                hasElec && hasWater -> {
                    sb.append("$timeStr ${formatExport(record.electricTotal!!)} ${formatExport(record.waterTotal!!)}")
                    if (record.electricPeak != null && record.electricValley != null) {
                        sb.append(" 峰${formatExport(record.electricPeak)} 谷${formatExport(record.electricValley)}")
                    }
                    if (hasNote) sb.append(" ${record.note}")
                    sb.appendLine()
                }
                hasElec -> {
                    sb.append("$timeStr ${formatExport(record.electricTotal!!)}")
                    if (record.electricPeak != null && record.electricValley != null) {
                        sb.append(" 峰${formatExport(record.electricPeak)} 谷${formatExport(record.electricValley)}")
                    }
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

            // 累计峰谷统计
            record.electricPeak?.let { totalPeak += it }
            record.electricValley?.let { totalValley += it }
            record.electricTotal?.let { totalKwh += it }
        }

        // ── 峰谷统计摘要 ──
        if (totalPeak > 0 || totalValley > 0) {
            sb.appendLine()
            sb.appendLine("# ═══════════════════════════")
            sb.appendLine("# 峰谷电统计 (所有记录累计)")
            sb.appendLine("# ═══════════════════════════")
            sb.appendLine("# 总峰电: ${formatExport(totalPeak)} 度")
            sb.appendLine("# 总谷电: ${formatExport(totalValley)} 度")
            if (totalPeak + totalValley > 0) {
                val peakRatio = totalPeak / (totalPeak + totalValley) * 100.0
                sb.appendLine("# 峰谷比: ${"%.1f".format(peakRatio)}% 峰 / ${"%.1f".format(100.0 - peakRatio)}% 谷")
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
