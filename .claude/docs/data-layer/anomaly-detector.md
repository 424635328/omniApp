# AnomalyDetector — 异常检测引擎

## Files
- Android: `app/src/main/java/com/example/energyflow/data/AnomalyDetector.kt`
- Shared KMP: `shared/src/commonMain/kotlin/com/example/energyflow/shared/AnomalyDetector.kt`

## 检测维度

### 1. 单调递增校验 (checkElectricMonotonic / checkWaterMonotonic)
- 新读数 < 该时间戳之前的最近一条记录的读数 → 警告
- 比较对象：`electricHistoryBefore(timestamp).lastOrNull()`，即时间上最近的前一条记录，不是历史最大值
- 用于检测：抄表错误、换表后未标记

### 2. 突增检测 (checkElectricSpike)
- 计算候选记录的日均用电 vs 最近 4 段历史日均
- ratio >= 5.0 → 警告
- 用于检测：大功率设备异常运行、输入错误

### 3. 批量导入递减检测 (MeterRepository.findBatchDropWarning)
- 检查导入候选与历史记录之间的读数递减
- 只检查 "previous 来自历史" 的对（id > 0）

## Warning 类型
```kotlin
sealed class AnomalyWarning {
    data class ReadingLowerThanPrevious(val message: String)
    data class SpikeDetected(val detail: String)
}
```

## 触发时机
- **单条插入**: `MeterRepository.smartInsert()` → `checkParseResult()` + `checkParseResultForSpike()`
- **表单保存**: `MainViewModel.validateAndSave()` → `collectAnomalyWarnings()`
- **批量导入**: `MeterRepository.batchInsert()` → `validateBatchCandidates()`

## 用户交互
检测到异常时弹出 `AnomalyWarningDialog`：
- "确认保存" → force save
- "标记为换表" → save with note "标记为换表"
- "返回修改" → cancel

## Shared KMP 版本
`shared/src/commonMain/kotlin/com/example/energyflow/shared/AnomalyDetector.kt`
`AnomalyDetectorShared.detect()` — 简化版，用于跨平台场景
- 读数递减：newVal < prevVal * 0.5（比 Android 版更宽松，Android 版是 < prevVal）
- 尖峰：lastDelta > avgDelta * 5 && lastDelta > 50
- 输入：newRecord + latestRecord + records 列表
- 输出：`List<Warning>` (sealed class: ReadingLowerThanPrevious / SpikeDetected)
