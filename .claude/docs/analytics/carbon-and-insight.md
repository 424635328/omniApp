# 碳足迹 & 洞察生成

## CarbonFootprint

### Files
- Android: `app/src/main/java/com/example/energyflow/data/CarbonFootprint.kt`
- Shared KMP: `shared/src/commonMain/kotlin/com/example/energyflow/shared/CarbonFootprint.kt`

### 计算公式
```
electricKgCO2 = kwh × electricFactor (默认 0.583 kg/kWh，中国电网平均)
gasKgCO2 = gasM3 × gasFactor (默认 2.02 kg/m³)
totalKgCO2 = electricKgCO2 + gasKgCO2
treeDays = totalKgCO2 / (treeKgPerYear / 365)  // 等效植树天数
```

### 绿色徽章 (GreenBadge)
| 徽章 | 条件 |
|------|------|
| FIRST_STEP | 有任何数据 |
| STREAK_3 | 连续 3+ 个月有记录 |
| ENERGY_SAVER | 最近月用电 < 上月 |
| PEAK_SHIFTER | 谷电占比 > 30% |
| GREEN_MONTH | 最近月碳排放 < 平均值 80% |
| CARBON_MASTER | 获得除自身外全部 5 个徽章 |

### 用户可配置因子
通过 `UserPreferences` 持久化：
- `carbonElectricFactor` (默认 0.583)
- `carbonGasFactor` (默认 2.02)
- `carbonTreeKgPerYear` (默认 20.0)

---

## InsightGenerator — 本地启发式洞察

### File
`app/src/main/java/com/example/energyflow/data/InsightGenerator.kt`

### 生成策略（按优先级返回第一个匹配）
1. **阶梯预警** — 本月用电 > 二档阈值 80%
2. **高温影响** — 近 3 天 >35°C + 日均用电飙升 >50%
3. **谷电偏低** — 近 7 天谷电占比 < 20%
4. **周末偏高** — 周末日均 > 工作日 × 1.3

### Insight 数据
```kotlin
data class Insight(
    val emoji: String,
    val title: String,
    val detail: String,
    val level: Level  // INFO, WARNING, CRITICAL
)
```

### 触发方式
`MainViewModel` 中通过 `allRecords.map { InsightGenerator.generate(records) }` 声明式计算，stateIn 缓存。

---

## EventImpactAnalyzer — 事件标签能耗影响

### File
`app/src/main/java/com/example/energyflow/data/EventImpactAnalyzer.kt`

### 分析逻辑
1. 从备注中提取标签：`#hashtag` 或已知电器词（冰箱/空调/洗衣机等）
2. 识别事件窗口：`打开/开启/开始` → `关闭/关了/停止`
3. 窗口内日均 vs 窗口外日均 → deltaKwh

### EventImpact
```kotlin
data class EventImpact(
    val tag: String,
    val eventDailyKwh: Double,      // 事件期间日均
    val nonEventDailyKwh: Double,   // 非事件期间日均
    val deltaKwh: Double,           // 差值
    val eventDays: Double,
    val nonEventDays: Double
)
```
