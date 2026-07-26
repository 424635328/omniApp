package com.example.energyflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.BillReportGenerator
import com.example.energyflow.data.BatchInsertResult
import com.example.energyflow.data.BillingRules
import com.example.energyflow.data.CarbonFootprint
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.data.ReportExporter
import com.example.energyflow.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BillingSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val repository: MeterRepository,
    private val costEngine: com.example.energyflow.data.CostEngine,
    private val carbonFootprint: CarbonFootprint
) : ViewModel() {

    companion object {
        private val jsonPretty = Json { prettyPrint = true }
        private val jsonLenient = Json { ignoreUnknownKeys = true }
    }
    val billingRulesFlow: Flow<BillingRules> = userPreferences.billingRules
    val deepSeekApiKeyFlow: Flow<String> = userPreferences.deepSeekApiKey
    val isDarkThemeFlow: Flow<Boolean> = userPreferences.isDarkTheme
    val followSystemThemeFlow: Flow<Boolean> = userPreferences.followSystemTheme
    val peakValleyExpandedFlow: Flow<Boolean> = userPreferences.peakValleyExpanded
    val themeDistEnabledFlow: Flow<Boolean> = userPreferences.themeDistEnabled

    // ── 碳足迹因子 ──
    val carbonElectricFactorFlow: Flow<Double> = userPreferences.carbonElectricFactor
    val carbonGasFactorFlow: Flow<Double> = userPreferences.carbonGasFactor
    val carbonTreeKgPerYearFlow: Flow<Double> = userPreferences.carbonTreeKgPerYear

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

    // ── 碳足迹因子更新（立即保存） ────────────────────────────
    fun updateCarbonElectricFactor(value: Double) = viewModelScope.launch {
        userPreferences.setCarbonElectricFactor(value)
    }
    fun updateCarbonGasFactor(value: Double) = viewModelScope.launch {
        userPreferences.setCarbonGasFactor(value)
    }
    fun updateCarbonTreeKgPerYear(value: Double) = viewModelScope.launch {
        userPreferences.setCarbonTreeKgPerYear(value)
    }

    // ── 报告图片导出 ──────────────────────────────────────────

    private val _reportImageUri = MutableStateFlow<android.net.Uri?>(null)
    val reportImageUri: StateFlow<android.net.Uri?> = _reportImageUri.asStateFlow()

    private val _reportExporting = MutableStateFlow(false)
    val reportExporting: StateFlow<Boolean> = _reportExporting.asStateFlow()

    suspend fun generateReportImage(context: android.content.Context, yearMonth: YearMonth = YearMonth.now()): android.net.Uri? {
        _reportExporting.value = true
        try {
            // 使用统一数据源
            val (data, _, isDark) = buildReportDataWithCarbon(yearMonth) ?: return null
            val content = BillReportGenerator.toReportContent(data, isDark)
            val uri = ReportExporter.export(context, content)
            _reportImageUri.value = uri
            return uri
        } finally {
            _reportExporting.value = false
        }
    }

    // ── 数据管理 ────────────────────────────────────────────

    fun deleteAllRecords() = viewModelScope.launch {
        repository.deleteAll()
    }

    // ── 计费规则模板 JSON 导入/导出 ─────────────────────────

    suspend fun exportRulesToJson(): String {
        val rules = billingRulesFlow.first()
        return jsonPretty.encodeToString(rules)
    }

    suspend fun importRulesFromJson(json: String): String {
        return try {
            val rules = jsonLenient.decodeFromString<BillingRules>(json)
            userPreferences.setBillingRules(rules)
            "计费规则模板导入成功"
        } catch (e: Exception) {
            "导入失败: ${e.message}"
        }
    }

    // ── 账单分享 ──────────────────────────────────────────

    /**
     * 构建报告数据（共享逻辑，避免重复查询数据库）。
     * 统一数据源：文本/HTML/图片报告都使用此方法。
     */
    private suspend fun buildReportDataWithCarbon(yearMonth: YearMonth): Triple<BillReportGenerator.ReportData, BillReportGenerator.ComparisonData?, Boolean>? {
        val electricRecords = repository.getElectricRecords().first()
        val waterRecords = repository.getWaterRecords().first()
        val gasRecords = repository.getAllRecords().first().filter { it.isGasRecorded }
        val notesRecords = repository.getRecordsWithNotes().first()
        val rules = billingRulesFlow.first()
        val isDark = isDarkThemeFlow.first()

        val data = BillReportGenerator.buildReportData(
            electricRecords, waterRecords, gasRecords, notesRecords, costEngine, rules, yearMonth
        ) ?: return null

        // Override carbon values with user-configurable factors
        val carbonResult = carbonFootprint.calculate(data.electricKwh, data.gasM3)
        val dataWithCarbon = data.copy(co2Kg = carbonResult.electricKgCO2, treeDays = carbonResult.treeDays)

        val comparison = BillReportGenerator.buildComparison(
            dataWithCarbon, electricRecords, waterRecords, gasRecords, notesRecords, costEngine, rules
        )

        // 计算上月费用（用于图片报告）
        val prevMonth = yearMonth.minusMonths(1)
        val prevMonthRecords = electricRecords.filter {
            YearMonth.from(it.timestamp) == prevMonth && it.electricTotal != null
        }.sortedBy { it.timestamp }
        val previousCost: Double? = if (prevMonthRecords.size >= 2) {
            val pFirst = prevMonthRecords.first()
            val pLast = prevMonthRecords.last()
            val pKwh = ((pLast.electricTotal ?: 0.0) - (pFirst.electricTotal ?: 0.0)).coerceAtLeast(0.0)
            val pPeakKwh = if (pLast.electricPeak != null && pFirst.electricPeak != null)
                ((pLast.electricPeak ?: 0.0) - (pFirst.electricPeak ?: 0.0)).coerceAtLeast(0.0) else 0.0
            val pValleyKwh = if (pLast.electricValley != null && pFirst.electricValley != null)
                ((pLast.electricValley ?: 0.0) - (pFirst.electricValley ?: 0.0)).coerceAtLeast(0.0) else 0.0
            val prevWaterMonth = waterRecords.filter {
                it.waterTotal != null && YearMonth.from(it.timestamp) == prevMonth
            }.sortedBy { it.timestamp }
            val prevWaterTons = if (prevWaterMonth.size >= 2)
                ((prevWaterMonth.last().waterTotal ?: 0.0) - (prevWaterMonth.first().waterTotal ?: 0.0)).coerceAtLeast(0.0) else 0.0
            val prevGasMonth = gasRecords.filter {
                it.gasTotal != null && YearMonth.from(it.timestamp) == prevMonth
            }.sortedBy { it.timestamp }
            val prevGasM3 = if (prevGasMonth.size >= 2)
                ((prevGasMonth.last().gasTotal ?: 0.0) - (prevGasMonth.first().gasTotal ?: 0.0)).coerceAtLeast(0.0) else 0.0
            val prevBill = costEngine.calculateBill(pKwh, pPeakKwh, pValleyKwh, prevWaterTons)
            prevBill.totalCost + prevGasM3 * rules.gasUnitPrice
        } else null

        val dataWithPreviousCost = dataWithCarbon.copy(previousCost = previousCost)

        return Triple(dataWithPreviousCost, comparison, isDark)
    }

    /**
     * 生成纯文本账单报告（指定月份）。
     */
    suspend fun generateShareReport(yearMonth: YearMonth = YearMonth.now()): String? {
        val (data, comparison, _) = buildReportDataWithCarbon(yearMonth) ?: return null
        return BillReportGenerator.generateTextReport(data, comparison)
    }

    /**
     * 生成 HTML 账单报告（指定月份）。
     */
    suspend fun generateShareHtml(yearMonth: YearMonth = YearMonth.now()): String? {
        val (data, comparison, isDark) = buildReportDataWithCarbon(yearMonth) ?: return null
        return BillReportGenerator.generateHtmlReport(data, comparison, isDark)
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
            val eps = 0.1
            fun same(d1: Double?, d2: Double?): Boolean = when {
                d1 == null && d2 == null -> true
                d1 == null || d2 == null -> false
                else -> kotlin.math.abs(d1 - d2) < eps
            }
            val elecSame = same(record.electricTotal, prev.electricTotal)
            val peakSame = same(record.electricPeak, prev.electricPeak)
            val valleySame = same(record.electricValley, prev.electricValley)
            val waterSame = same(record.waterTotal, prev.waterTotal)
            val gasSame = same(record.gasTotal, prev.gasTotal)
            !(elecSame && peakSame && valleySame && waterSame && gasSame)
        }
    }

    private fun buildExportText(records: List<MeterRecord>): String {
        if (records.isEmpty()) return ""

        val sb = StringBuilder()
        val grouped = records.groupBy { it.timestamp.toLocalDate() }
        val sortedDates = grouped.keys.sorted()

        for (date in sortedDates) {
            val dailyRecords = grouped[date] ?: continue
            val latest = dailyRecords.maxBy { it.timestamp }

            sb.appendLine("${date.monthValue}.${date.dayOfMonth}")
            val timeStr = "${latest.timestamp.hour.toString().padStart(2, '0')}.${latest.timestamp.minute.toString().padStart(2, '0')}"

            val electric = dailyRecords.firstOrNull { it.electricTotal != null }?.electricTotal
            val peak = dailyRecords.firstOrNull { it.electricPeak != null }?.electricPeak
            val valley = dailyRecords.firstOrNull { it.electricValley != null }?.electricValley
            val water = dailyRecords.firstOrNull { it.waterTotal != null }?.waterTotal
            val gas = dailyRecords.firstOrNull { it.gasTotal != null }?.gasTotal
            val note = dailyRecords.firstOrNull { !it.note.isNullOrBlank() }?.note

            val parts = mutableListOf<String>()
            if (electric != null) parts.add("电${formatExport(electric)}")
            if (water != null) parts.add("水${formatExport(water)}")
            if (gas != null) parts.add("气${formatExport(gas)}")
            if (peak != null && valley != null) {
                parts.add("峰${formatExport(peak)}")
                parts.add("谷${formatExport(valley)}")
            }
            sb.append(timeStr)
            if (parts.isNotEmpty()) sb.append(" ${parts.joinToString(" ")}")
            if (!note.isNullOrBlank()) sb.append(" $note")
            sb.appendLine()
        }

        val allElectric = records
            .filter { it.isElectricRecorded && it.electricTotal != null }
            .sortedBy { it.timestamp }
            .mapNotNull { it.electricTotal }

        val allPeaks = records
            .filter { it.electricPeak != null }
            .sortedBy { it.timestamp }
            .mapNotNull { it.electricPeak }

        val allValleys = records
            .filter { it.electricValley != null }
            .sortedBy { it.timestamp }
            .mapNotNull { it.electricValley }

        val sumLine = mutableListOf<String>()
        if (allElectric.size >= 2)
            sumLine.add("总电: ${formatExport(allElectric.last() - allElectric.first())} 度")
        if (allPeaks.size >= 2)
            sumLine.add("峰电: ${formatExport(allPeaks.last() - allPeaks.first())} 度")
        if (allValleys.size >= 2)
            sumLine.add("谷电: ${formatExport(allValleys.last() - allValleys.first())} 度")

        if (sumLine.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("# ${sumLine.joinToString(" · ")}")
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
