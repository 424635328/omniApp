package com.example.energyflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.AnomalyDetector
import com.example.energyflow.data.AnomalyWarning
import com.example.energyflow.data.BillingRules
import com.example.energyflow.data.BatchInsertResult
import com.example.energyflow.data.InsertResult
import com.example.energyflow.data.InsightGenerator
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.data.ParseResult
import com.example.energyflow.data.SmartInputParser
import com.example.energyflow.data.UserPreferences
import com.example.energyflow.data.WeatherRepository
import com.example.energyflow.data.WeatherResult
import com.example.energyflow.ui.components.RecordData
import com.example.energyflow.ui.theme.ThemeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MeterRepository,
    private val anomalyDetector: AnomalyDetector,
    private val userPreferences: UserPreferences,
    private val weatherRepository: WeatherRepository,
    private val deepSeekRepository: com.example.energyflow.data.DeepSeekRepository
) : ViewModel() {

    // ── 分页加载：首屏只取最近 150 条 ──
    // 增大此值 → Room 自动以更大 LIMIT 重新查询（保持响应性）
    private val _loadLimit = MutableStateFlow(150)
    val allRecords: StateFlow<List<MeterRecord>> = _loadLimit
        .flatMapLatest { limit: Int ->
            kotlinx.coroutines.flow.flow {
                kotlinx.coroutines.delay(80) // 让 SplashScreen 先完成渲染
                emitAll(repository.getRecordsLimited(limit))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 筛选栏计数（轻量 COUNT 查询，不检索全表数据）
    val filterCounts: StateFlow<Map<String, Int>> = combine(
        repository.getRecordCount(),
        repository.getElectricCount(),
        repository.getWaterCount(),
        repository.getGasCount(),
        repository.getNoteCount()
    ) { total: Int, elec: Int, water: Int, gas: Int, notes: Int ->
        mapOf(
            "total" to total,
            "electric" to elec,
            "water" to water,
            "gas" to gas,
            "notes" to notes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recordCount: StateFlow<Int> = repository.getRecordCount()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0)

    val peakValleyExpanded: StateFlow<Boolean> = userPreferences.peakValleyExpanded
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false)

    // ── 阶梯进度 + 环比 ──
    data class TierProgress(
        val currentMonthKwh: Double = 0.0,
        val tier1Limit: Double = 230.0,
        val tier2Limit: Double = 400.0,
        val progress: Float = 0f,
        val tierColor: TierLevel = TierLevel.Tier1,
        val momChange: Double? = null
    )

    enum class TierLevel { Tier1, Tier2, Tier3 }

    private val _tierProgress = MutableStateFlow(TierProgress())
    val tierProgress: StateFlow<TierProgress> = _tierProgress.asStateFlow()

    // ── 缓存 billingRules，避免每次读 DataStore ──
    private val billingRules: StateFlow<BillingRules> = userPreferences.billingRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillingRules())

    // ── 所有 MutableStateFlow 声明必须在 init 块之前 ──
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _anomalyWarnings = MutableStateFlow<List<AnomalyWarning>>(emptyList())
    val anomalyWarnings: StateFlow<List<AnomalyWarning>> = _anomalyWarnings.asStateFlow()

    private val _pendingSaveData = MutableStateFlow<RecordData?>(null)
    val pendingSaveData: StateFlow<RecordData?> = _pendingSaveData.asStateFlow()
    private var pendingMutation: PendingMutation? = null

    private val _showAnomalyDialog = MutableStateFlow(false)
    val showAnomalyDialog: StateFlow<Boolean> = _showAnomalyDialog.asStateFlow()

    // ── 常用标签（从历史备注自动提取） ──
    val commonTags: StateFlow<List<String>> = repository.getCommonTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 主动洞察（声明式 stateIn，无 NPE 风险） ──
    val insight: StateFlow<InsightGenerator.Insight?> = allRecords
        .map { records -> InsightGenerator.generate(records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // 1. 阶梯进度：记录变化时重算
        viewModelScope.launch {
            allRecords.collect { records ->
                withContext(Dispatchers.IO) { computeTierProgress(records) }
            }
        }
        // 2. 天气主题：仅最新记录日期变化时获取
        viewModelScope.launch {
            allRecords
                .map { records -> records.maxByOrNull { it.timestamp }?.timestamp?.toLocalDate() }
                .distinctUntilChanged()
                .collect { latestDate ->
                    if (latestDate != null) {
                        withContext(Dispatchers.IO) { fetchWeatherForTheme(latestDate) }
                    }
                }
        }
    }

    private var lastWeatherFetchDate: java.time.LocalDate? = null

    private suspend fun fetchWeatherForTheme(date: java.time.LocalDate) {
        if (date == lastWeatherFetchDate) return
        lastWeatherFetchDate = date
        try {
            val result = weatherRepository.fetchHistorical(date, date)
            if (result is WeatherResult.Success && result.data.isNotEmpty()) {
                ThemeState.applyWeatherTheme(result.data.first().tempMax)
            }
        } catch (_: Exception) {}
    }

    private suspend fun computeTierProgress(records: List<MeterRecord>) {
        val now = YearMonth.now()
        val prev = now.minusMonths(1)
        val rules = billingRules.value

        // 本月电表记录
        val thisMonth = records.filter {
            it.isElectricRecorded && it.electricTotal != null &&
            YearMonth.from(it.timestamp) == now
        }.sortedBy { it.timestamp }

        val currentKwh = if (thisMonth.size >= 2) {
            (thisMonth.last().electricTotal ?: 0.0) - (thisMonth.first().electricTotal ?: 0.0)
        } else 0.0

        // 上月同口径
        val lastMonth = records.filter {
            it.isElectricRecorded && it.electricTotal != null &&
            YearMonth.from(it.timestamp) == prev
        }.sortedBy { it.timestamp }

        val prevKwh = if (lastMonth.size >= 2) {
            (lastMonth.last().electricTotal ?: 0.0) - (lastMonth.first().electricTotal ?: 0.0)
        } else null

        val momChange = if (prevKwh != null && prevKwh > 0) {
            (currentKwh - prevKwh) / prevKwh * 100.0
        } else null

        val progress = (currentKwh / rules.electricTier2Limit).toFloat().coerceIn(0f, 1.5f)
        val tierColor = when {
            currentKwh > rules.electricTier2Limit -> TierLevel.Tier3
            currentKwh > rules.electricTier1Limit -> TierLevel.Tier2
            else -> TierLevel.Tier1
        }

        _tierProgress.value = TierProgress(
            currentMonthKwh = currentKwh,
            tier1Limit = rules.electricTier1Limit,
            tier2Limit = rules.electricTier2Limit,
            progress = progress,
            tierColor = tierColor,
            momChange = momChange
        )
    }

    // 加载更多：增大 LIMIT → flatMapLatest 自动重新查询
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    fun loadMore() {
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        _loadLimit.value += 50  // 触发 flatMapLatest 以新 LIMIT 重新查询
        _isLoadingMore.value = false
    }

    // 重置为初始化数量
    fun reloadAll() { _loadLimit.value = 150 }

    fun setPeakValleyExpanded(expanded: Boolean) {
        viewModelScope.launch { userPreferences.setPeakValleyExpanded(expanded) }
    }

    // ── OCR 扫表结果自动回填 ──
    private val _pendingOcrData = MutableStateFlow<RecordData?>(null)
    val pendingOcrData: StateFlow<RecordData?> = _pendingOcrData.asStateFlow()

    /** OCR扫描成功后将结果解析并准备回填到添加表单 */
    fun ocrAutoFill(rawText: String) {
        val parser = SmartInputParser()
        val results = parser.parseWithContext(rawText)
        val successes = results.filterIsInstance<ParseResult.Success>()

        if (successes.isNotEmpty()) {
            val first = successes.first()
            _pendingOcrData.value = RecordData(
                timestamp = first.timestamp,
                isElectric = first.isElectric,
                electricTotal = first.electricTotal,
                electricPeak = first.electricPeak,
                electricValley = first.electricValley,
                isWater = first.isWater,
                waterTotal = first.waterTotal,
                note = first.note
            )
        } else {
            val numbers = Regex("""\d+\.?\d*""").findAll(rawText).map { it.value.toDoubleOrNull() }.filterNotNull().toList()
            if (numbers.isNotEmpty()) {
                val now = java.time.LocalDateTime.now()
                _pendingOcrData.value = RecordData(
                    timestamp = now,
                    isElectric = true,
                    electricTotal = numbers.firstOrNull(),
                    electricPeak = null,
                    electricValley = null,
                    isWater = numbers.size > 1,
                    waterTotal = numbers.getOrElse(1) { null },
                    note = null
                )
            }
        }
    }

    fun clearPendingOcr() { _pendingOcrData.value = null }

    /**
     * 校验并保存记录。如果发现异常则弹出确认框，不直接保存。
     */
    fun validateAndSave(data: RecordData) {
        if (!data.isElectric && !data.isWater && !data.isGas) {
            _uiState.value = UiState.Error("请至少输入电表、水表或燃气数据")
            return
        }
        if (data.isElectric && data.electricTotal == null) {
            _uiState.value = UiState.Error("请输入电表读数")
            return
        }
        if (data.isWater && data.waterTotal == null) {
            _uiState.value = UiState.Error("请输入水表读数")
            return
        }
        if (data.isGas && data.gasTotal == null) {
            _uiState.value = UiState.Error("请输入燃气读数")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val warnings = collectAnomalyWarnings(data)

                if (warnings.isNotEmpty()) {
                    _anomalyWarnings.value = warnings
                    _pendingSaveData.value = data
                    pendingMutation = PendingMutation.Insert(data)
                    _showAnomalyDialog.value = true
                    _uiState.value = UiState.Idle
                    return@launch
                }

                // 无异常，直接保存
                performSave(data)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("保存失败: ${e.message}")
            }
        }
    }

    /**
     * 用户点击了"确认保存"（无视异常警告）。
     */
    fun confirmSaveWithAnomaly() {
        val mutation = pendingMutation ?: _pendingSaveData.value?.let { PendingMutation.Insert(it) } ?: return
        viewModelScope.launch {
            when (mutation) {
                is PendingMutation.Insert -> performSave(mutation.data)
                is PendingMutation.Update -> performUpdate(mutation.original, mutation.data)
            }
            clearAnomalyPending()
        }
    }

    /**
     * 用户点击了"标记为换表"（强制保存，忽略异常）。
     */
    fun forceSaveAsMeterReplacement() {
        val mutation = pendingMutation ?: _pendingSaveData.value?.let { PendingMutation.Insert(it) } ?: return
        viewModelScope.launch {
            when (mutation) {
                is PendingMutation.Insert -> {
                    val record = MeterRecord(
                        timestamp = mutation.data.timestamp,
                        isElectricRecorded = mutation.data.isElectric,
                        electricTotal = mutation.data.electricTotal,
                        electricPeak = mutation.data.electricPeak,
                        electricValley = mutation.data.electricValley,
                        isWaterRecorded = mutation.data.isWater,
                        waterTotal = mutation.data.waterTotal,
                        isGasRecorded = mutation.data.isGas,
                        gasTotal = mutation.data.gasTotal,
                        note = "标记为换表"
                    )
                    repository.insert(record)
                    _uiState.value = UiState.Success("已标记为换表并保存")
                }
                is PendingMutation.Update -> performUpdate(mutation.original, mutation.data)
            }
            clearAnomalyPending()
        }
    }

    /**
     * 用户点击了"取消"（返回修改）。
     */
    fun cancelSaveWithAnomaly() {
        clearAnomalyPending()
    }

    private suspend fun performSave(data: RecordData) {
        val record = MeterRecord(
            timestamp = data.timestamp,
            isElectricRecorded = data.isElectric,
            electricTotal = data.electricTotal,
            electricPeak = data.electricPeak,
            electricValley = data.electricValley,
            isWaterRecorded = data.isWater,
            waterTotal = data.waterTotal,
            isGasRecorded = data.isGas,
            gasTotal = data.gasTotal,
            note = data.note
        )
        repository.insert(record)
        _uiState.value = UiState.Success("记录已保存")
    }

    fun insertFromRecordData(data: RecordData) {
        if (!data.isElectric && !data.isWater && !data.isGas) {
            _uiState.value = UiState.Error("请至少输入电表、水表或燃气数据")
            return
        }
        if (data.isElectric && data.electricTotal == null) {
            _uiState.value = UiState.Error("请输入电表读数")
            return
        }
        if (data.isWater && data.waterTotal == null) {
            _uiState.value = UiState.Error("请输入水表读数")
            return
        }
        if (data.isGas && data.gasTotal == null) {
            _uiState.value = UiState.Error("请输入燃气读数")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val record = MeterRecord(
                    timestamp = data.timestamp,
                    isElectricRecorded = data.isElectric,
                    electricTotal = data.electricTotal,
                    electricPeak = data.electricPeak,
                    electricValley = data.electricValley,
                    isWaterRecorded = data.isWater,
                    waterTotal = data.waterTotal,
                    isGasRecorded = data.isGas,
                    gasTotal = data.gasTotal,
                    note = data.note
                )
                repository.insert(record)
                _uiState.value = UiState.Success("记录已保存")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("保存失败: ${e.message}")
            }
        }
    }

    fun updateRecord(original: MeterRecord, data: RecordData) {
        if (!data.isElectric && !data.isWater && !data.isGas) {
            _uiState.value = UiState.Error("请至少输入电表、水表或燃气数据")
            return
        }
        if (data.isElectric && data.electricTotal == null) {
            _uiState.value = UiState.Error("请输入电表读数")
            return
        }
        if (data.isWater && data.waterTotal == null) {
            _uiState.value = UiState.Error("请输入水表读数")
            return
        }
        if (data.isGas && data.gasTotal == null) {
            _uiState.value = UiState.Error("请输入燃气读数")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val warnings = collectAnomalyWarnings(data)
                if (warnings.isNotEmpty()) {
                    _anomalyWarnings.value = warnings
                    _pendingSaveData.value = data
                    pendingMutation = PendingMutation.Update(original, data)
                    _showAnomalyDialog.value = true
                    _uiState.value = UiState.Idle
                    return@launch
                }
                performUpdate(original, data)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }

    fun smartInsert(input: String) {
        if (input.isBlank()) {
            _uiState.value = UiState.Error("输入不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                when (val result = repository.smartInsert(input)) {
                    is InsertResult.Success -> _uiState.value = UiState.Success("记录已保存")
                    is InsertResult.Warning -> _uiState.value = UiState.Warning(result.message)
                    is InsertResult.Error -> {
                        // ── DeepSeek 降级解析 ──
                        val aiParsed = deepSeekRepository.parseNaturalInput(input)
                        if (aiParsed != null) {
                            // AI 返回了结构化文本，再走一次解析
                            when (val retryResult = repository.smartInsert(aiParsed, force = true)) {
                                is InsertResult.Success ->
                                    _uiState.value = UiState.Success("AI 识别成功，记录已保存")
                                is InsertResult.Warning ->
                                    _uiState.value = UiState.Warning("AI 识别: ${retryResult.message}")
                                is InsertResult.Error ->
                                    _uiState.value = UiState.Error(result.message)
                            }
                        } else {
                            _uiState.value = UiState.Error(result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("解析失败: ${e.message}")
            }
        }
    }

    fun batchImport(input: String) {
        if (input.isBlank()) {
            _uiState.value = UiState.Error("输入不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                when (val result = repository.batchInsert(input)) {
                    is BatchInsertResult.Success -> {
                        _uiState.value = UiState.Success("成功导入 ${result.count} 条记录")
                    }
                    is BatchInsertResult.Warning -> {
                        val msg = buildString {
                            append("导入已拦截，请先检查异常读数")
                            if (result.warnings.isNotEmpty()) {
                                append("\n⚠️ ${result.warnings.first()}")
                            }
                        }
                        _uiState.value = UiState.Warning(msg)
                    }
                    is BatchInsertResult.PartialSuccess -> {
                        _uiState.value = UiState.Success(
                            "成功导入 ${result.successCount} 条，${result.errors.size} 条失败"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("导入失败: ${e.message}")
            }
        }
    }

    private val _pendingDelete = MutableStateFlow<MeterRecord?>(null)
    val pendingDelete: StateFlow<MeterRecord?> = _pendingDelete.asStateFlow()

    fun softDelete(record: MeterRecord) {
        viewModelScope.launch {
            try {
                repository.delete(record)
                _pendingDelete.value = record
            } catch (e: Exception) {
                _uiState.value = UiState.Error("删除失败: ${e.message}")
            }
        }
    }

    fun undoDelete() {
        val record = _pendingDelete.value ?: return
        viewModelScope.launch {
            try {
                repository.insert(record)
                _pendingDelete.value = null
                _uiState.value = UiState.Success("已撤销删除")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("撤销失败: ${e.message}")
            }
        }
    }

    fun finalizeDelete() {
        _pendingDelete.value = null
    }

    fun deleteRecord(record: MeterRecord) {
        viewModelScope.launch {
            try {
                repository.delete(record)
                _uiState.value = UiState.Success("记录已删除")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("删除失败: ${e.message}")
            }
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }

    fun deleteAllRecords() {
        viewModelScope.launch {
            try {
                repository.deleteAll()
                _uiState.value = UiState.Success("所有记录已删除")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("删除失败: ${e.message}")
            }
        }
    }

    private suspend fun collectAnomalyWarnings(data: RecordData): List<AnomalyWarning> {
        val warnings = mutableListOf<AnomalyWarning>()

        if (data.isElectric && data.electricTotal != null) {
            anomalyDetector.checkElectricMonotonic(data.electricTotal, data.timestamp)?.let {
                warnings.add(AnomalyWarning.ReadingLowerThanPrevious(it))
            }
            anomalyDetector.checkElectricSpike(data.electricTotal, data.timestamp)?.let {
                warnings.add(AnomalyWarning.SpikeDetected(it))
            }
        }
        if (data.isWater && data.waterTotal != null) {
            anomalyDetector.checkWaterMonotonic(data.waterTotal, data.timestamp)?.let {
                warnings.add(AnomalyWarning.ReadingLowerThanPrevious(it))
            }
        }

        return warnings
    }

    private suspend fun performUpdate(original: MeterRecord, data: RecordData) {
        val updated = original.copy(
            timestamp = data.timestamp,
            isElectricRecorded = data.isElectric,
            electricTotal = data.electricTotal,
            electricPeak = data.electricPeak,
            electricValley = data.electricValley,
            isWaterRecorded = data.isWater,
            waterTotal = data.waterTotal,
            isGasRecorded = data.isGas,
            gasTotal = data.gasTotal,
            note = data.note
        )
        repository.update(updated)
        _uiState.value = UiState.Success("记录已更新")
    }

    private fun clearAnomalyPending() {
        _showAnomalyDialog.value = false
        _pendingSaveData.value = null
        pendingMutation = null
        _anomalyWarnings.value = emptyList()
    }
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val message: String) : UiState()
    data class Warning(val message: String) : UiState()
    data class Error(val message: String) : UiState()
}

private sealed class PendingMutation {
    data class Insert(val data: RecordData) : PendingMutation()
    data class Update(val original: MeterRecord, val data: RecordData) : PendingMutation()
}
