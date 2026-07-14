package com.example.energyflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.AnomalyDetector
import com.example.energyflow.data.AnomalyWarning
import com.example.energyflow.data.BatchInsertResult
import com.example.energyflow.data.InsertResult
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.data.UserPreferences
import com.example.energyflow.ui.components.RecordData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MeterRepository,
    private val anomalyDetector: AnomalyDetector,
    private val userPreferences: UserPreferences
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

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── 异常检测弹窗 ────────────────────────────────────────
    private val _anomalyWarnings = MutableStateFlow<List<AnomalyWarning>>(emptyList())
    val anomalyWarnings: StateFlow<List<AnomalyWarning>> = _anomalyWarnings.asStateFlow()

    private val _pendingSaveData = MutableStateFlow<RecordData?>(null)
    val pendingSaveData: StateFlow<RecordData?> = _pendingSaveData.asStateFlow()
    private var pendingMutation: PendingMutation? = null

    private val _showAnomalyDialog = MutableStateFlow(false)
    val showAnomalyDialog: StateFlow<Boolean> = _showAnomalyDialog.asStateFlow()

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

        viewModelScope.launch {
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
                    is InsertResult.Error -> _uiState.value = UiState.Error(result.message)
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
