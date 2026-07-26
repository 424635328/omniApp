# MeterRecord — 核心数据模型

## Entity Definition
`app/src/main/java/com/example/energyflow/data/MeterRecord.kt`

```kotlin
@Entity(tableName = "meter_records", indices = [Index(value = ["timestamp"])])
@Immutable  // Compose 稳定性标注，避免不必要重组
data class MeterRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: LocalDateTime,
    // 电表
    val isElectricRecorded: Boolean = false,
    val electricTotal: Double? = null,
    val electricPeak: Double? = null,
    val electricValley: Double? = null,
    // 水表
    val isWaterRecorded: Boolean = false,
    val waterTotal: Double? = null,
    // 燃气表
    val isGasRecorded: Boolean = false,
    val gasTotal: Double? = null,
    // 备注
    val note: String? = null
)
```

## Key Invariants
- `electricTotal` = 峰+谷总读数（累计值，非增量）
- `electricPeak`/`electricValley` 可独立记录，也可同时记录
- `waterTotal`/`gasTotal` 同理，累计值
- timestamp 精确到分钟级，索引加速范围查询
- 相邻记录的差值 = 该时段消耗量（`calculateConsumption()` in MeterRepository）

## DAO: MeterRecordDao
`app/src/main/java/com/example/energyflow/data/MeterRecordDao.kt`

核心查询：
- `getAllRecords()` → Flow, timestamp DESC + id DESC
- `getRecordsLimited(limit)` → 分页加载（DAO/Repository 默认 limit=200；首屏 150 条是 MainViewModel.kt:57 `_loadLimit` 传入的值）
- `getElectricRecords()` / `getWaterRecords()` / `getGasRecords()` → 按类型筛选
- `getPreviousRecord(currentTime)` → 找到当前时间之前的最近记录

## Repository: MeterRepository
`app/src/main/java/com/example/energyflow/data/MeterRepository.kt`

核心职责：
1. **smartInsert(input, force)** — 单条智能插入
   - SmartInputParser 解析 → AnomalyDetector 校验 → 去重检查 → Room insert → reLearnDebounced
2. **batchInsert(input, force)** — 批量导入
   - 日期缺口插值 (interpolateGaps, MeterRepository.kt:145) → 批内去重 → 历史去重 → 批量 insert
3. **interpolateGaps(records)** — 电表/水表独立插值填补日期缺口
4. **calculateConsumption(currentRecord)** — 计算与前一记录的消耗差值，返回 `ConsumptionResult`
5. **reLearnDebounced()** — 5分钟节流触发 AdaptiveClassifier.reLearn()

## InsertResult / BatchInsertResult
```kotlin
sealed class InsertResult {
    data class Success(val id: Long, val record: MeterRecord)
    data class Warning(val message: String)   // AnomalyDetector 拦截
    data class Error(val message: String)     // 解析失败/重复
}

sealed class BatchInsertResult {
    data class Success(val count: Int)
    data class Warning(val warnings: List<String>)
    data class PartialSuccess(val successCount: Int, val errors: List<String>)
}
```

## ConsumptionResult
`calculateConsumption()` 的返回类型（MeterRepository.kt:428-438）：
```kotlin
sealed class ConsumptionResult {
    data class Success(
        val electricConsumption: Double?,
        val waterConsumption: Double?,
        val daysBetween: Long,
        val dailyElectricConsumption: Double?,
        val dailyWaterConsumption: Double?
    )
    object NoPreviousRecord
}
```

## 去重逻辑
`areValuesSame(a, b)` — 容差 0.1，null 对 null = 相同，null 对值 = 不同

## 相关文档
- 输入解析: `.claude/docs/data-layer/smart-input-parser.md`
- 异常检测: `.claude/docs/data-layer/anomaly-detector.md`
- DAO 完整查询列表: `.claude/docs/architecture/app-entry-and-di.md`
