package com.example.energyflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.energyflow.data.AnomalyDetector
import com.example.energyflow.data.AnomalyWarning
import com.example.energyflow.data.BatchInsertResult
import com.example.energyflow.data.InsertResult
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import com.example.energyflow.ui.components.RecordData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MeterRepository,
    private val anomalyDetector: AnomalyDetector
) : ViewModel() {

    val allRecords: StateFlow<List<MeterRecord>> = repository.getAllRecords()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val recordCount: StateFlow<Int> = repository.getRecordCount()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── 异常检测弹窗 ────────────────────────────────────────
    private val _anomalyWarnings = MutableStateFlow<List<AnomalyWarning>>(emptyList())
    val anomalyWarnings: StateFlow<List<AnomalyWarning>> = _anomalyWarnings.asStateFlow()

    private val _pendingSaveData = MutableStateFlow<RecordData?>(null)
    val pendingSaveData: StateFlow<RecordData?> = _pendingSaveData.asStateFlow()

    private val _showAnomalyDialog = MutableStateFlow(false)
    val showAnomalyDialog: StateFlow<Boolean> = _showAnomalyDialog.asStateFlow()

    /**
     * 校验并保存记录。如果发现异常则弹出确认框，不直接保存。
     */
    fun validateAndSave(data: RecordData) {
        if (!data.isElectric && !data.isWater) {
            _uiState.value = UiState.Error("请至少输入电表或水表数据")
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

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // 异常检测
                val warnings = mutableListOf<AnomalyWarning>()

                if (data.isElectric && data.electricTotal != null) {
                    val monotonicWarning = anomalyDetector.checkElectricMonotonic(data.electricTotal)
                    if (monotonicWarning != null) {
                        warnings.add(AnomalyWarning.ReadingLowerThanPrevious(monotonicWarning))
                    }
                    val spikeWarning = anomalyDetector.checkElectricSpike(data.electricTotal)
                    if (spikeWarning != null) {
                        warnings.add(AnomalyWarning.SpikeDetected(spikeWarning))
                    }
                }
                if (data.isWater && data.waterTotal != null) {
                    val monotonicWarning = anomalyDetector.checkWaterMonotonic(data.waterTotal)
                    if (monotonicWarning != null) {
                        warnings.add(AnomalyWarning.ReadingLowerThanPrevious(monotonicWarning))
                    }
                }

                if (warnings.isNotEmpty()) {
                    _anomalyWarnings.value = warnings
                    _pendingSaveData.value = data
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
        val data = _pendingSaveData.value ?: return
        viewModelScope.launch {
            performSave(data)
            _showAnomalyDialog.value = false
            _pendingSaveData.value = null
            _anomalyWarnings.value = emptyList()
        }
    }

    /**
     * 用户点击了"取消"（返回修改）。
     */
    fun cancelSaveWithAnomaly() {
        _showAnomalyDialog.value = false
        _pendingSaveData.value = null
        _anomalyWarnings.value = emptyList()
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
            note = data.note
        )
        repository.insert(record)
        _uiState.value = UiState.Success("记录已保存")
    }

    fun insertFromRecordData(data: RecordData) {
        if (!data.isElectric && !data.isWater) {
            _uiState.value = UiState.Error("请至少输入电表或水表数据")
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
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val updated = original.copy(
                    timestamp = data.timestamp,
                    isElectricRecorded = data.isElectric,
                    electricTotal = data.electricTotal,
                    electricPeak = data.electricPeak,
                    electricValley = data.electricValley,
                    isWaterRecorded = data.isWater,
                    waterTotal = data.waterTotal,
                    note = data.note
                )
                repository.update(updated)
                _uiState.value = UiState.Success("记录已更新")
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
                    is BatchInsertResult.SuccessWithWarnings -> {
                        val msg = buildString {
                            append("成功导入 ${result.count} 条记录")
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
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val message: String) : UiState()
    data class Warning(val message: String) : UiState()
    data class Error(val message: String) : UiState()
}
