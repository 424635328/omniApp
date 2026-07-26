# ChartViewModel & ChartScreen — 分析页架构

## File
- ViewModel: `app/src/main/java/com/example/energyflow/ui/chart/ChartViewModel.kt` (~1070 lines)
- Screen: `app/src/main/java/com/example/energyflow/ui/chart/ChartScreen.kt` (~2700+ lines)

## ChartViewModel 状态模型

### 三表类型切换
```kotlin
enum class MeterType { ELECTRIC, WATER, GAS }
```
切换时触发 `recalculateForCurrentType()`，每种表有独立的状态流。

### 电表状态流
| StateFlow | 类型 | 用途 |
|-----------|------|------|
| `chartData` | `ChartData` | 图表数据（记录+日消耗+标注+预报） |
| `billResult` | `BillData?` | 当前时间范围的账单 |
| `prediction` | `MonthPrediction?` | 月度预测 |
| `predictedBill` | `PredictedBill?` | 预测账单费用 |
| `predictionTracking` | `PredictionTracking?` | 预测 vs 实际偏差 |
| `eventImpacts` | `List<EventImpact>` | 事件标签能耗影响 |
| `aiAnalysis` | `String?` | DeepSeek AI 分析结果 |
| `aiLoading` | `Boolean` | AI 分析加载中 |
| `carbonData` | `CarbonResult?` | 碳足迹 |
| `weatherData` | `List<DailyWeather>` | 天气数据（7天预报/历史） |
| `electricRecords` | `List<MeterRecord>` | 电表记录（Room Flow，WhileSubscribed） |

### 水表状态流
| StateFlow | 类型 | 用途 |
|-----------|------|------|
| `waterChartData` | `ChartData` | 水表图表数据 |
| `waterBillResult` | `WaterBillData?` | 水费账单 |
| `waterPrediction` | `MonthPrediction?` | 水表月度预测 |
| `waterRecords` | `List<MeterRecord>` | 水表记录（Room Flow，WhileSubscribed） |

### 气表状态流
| StateFlow | 类型 | 用途 |
|-----------|------|------|
| `gasChartData` | `ChartData` | 气表图表数据 |
| `gasRecords` | `List<MeterRecord>` | 气表记录（Room Flow，WhileSubscribed） |

### 公共状态
| StateFlow | 类型 | 用途 |
|-----------|------|------|
| `selectedMeterType` | `MeterType` | 当前选中的表类型 |
| `timeRange` | `TimeRange` | 时间范围 (WEEK/MONTH/YEAR/ALL) |
| `showCost` | `Boolean` | kWh/¥ 切换 |
| `weatherLoading` | `Boolean` | 天气加载中 |
| `weatherError` | `String?` | 天气获取错误 |
| `notesRecords` | `List<MeterRecord>` | 带备注的记录（Room Flow，WhileSubscribed） |

## 数据类
```kotlin
@Immutable data class ChartData(
    val records: List<MeterRecord>,
    val dailyConsumptions: List<DailyConsumption>,
    val annotations: List<MeterRecord>,
    val timeRange: TimeRange,
    val forecastConsumptions: List<DailyConsumption> = emptyList()
)

@Immutable data class DailyConsumption(
    val date: LocalDateTime,
    val consumption: Double,      // 该段总消耗
    val dailyConsumption: Double, // 日均消耗
    val daysBetween: Long,        // 间隔天数
    val estimatedCost: Double = 0.0  // -1.0 = 投影数据点
)

enum class TimeRange { WEEK, MONTH, YEAR, ALL }

@Immutable data class BillData(
    val totalKwh: Double,
    val peakKwh: Double,
    val valleyKwh: Double,
    val waterTons: Double,
    val electricCost: Double,
    val waterCost: Double,
    val totalCost: Double,
    val peakPrice: Double,
    val valleyPrice: Double,
    val flatPrice: Double,
    val waterPrice: Double
)

@Immutable data class PredictedBill(val totalKwh: Double, val predictedCost: Double)

@Immutable data class PredictionTracking(
    val yearMonth: String,
    val savedDay: Int,
    val predictedTodayKwh: Double,
    val actualTodayKwh: Double,
    val varianceKwh: Double,
    val variancePercent: Double
)

@Immutable data class WaterBillData(
    val totalTons: Double,
    val waterCost: Double,
    val waterPrice: Double,
    val tier1Limit: Double,
    val tier2Limit: Double
)
```

## 核心计算流程

### recalculateAnalytics (电表)
1. 按 TimeRange 窗口过滤记录
2. `prependBaseline()` — 补入窗口前基线记录（确保插值覆盖窗口第一天）
3. 计算峰/谷/总用电增量
4. `CostEngine.calculateBill()` → BillData
5. `CarbonFootprint.calculate()` → CarbonResult
6. `PredictiveAnalyzer.predictMonth()` → MonthPrediction
7. 预测账单: peakRatio/valleyRatio × predictedTotal → CostEngine
8. 预测快照: DataStore 持久化 + 预测跟踪对比
9. `EventImpactAnalyzer.analyzeWithRecords()` → 事件影响
10. `computeForecastConsumptions()` — 从明天到月底的虚线投影点
11. `updateChartData()` → ChartData

### calculateDailyConsumptions (插值)
- 相邻记录间隔 N 天 → 生成 N 个数据点，消耗均摊
- 同一天多条记录 → 只保留最新时间点
- `estimatedCost = -1.0` 标记为投影数据点

### 天气集成
- `autoFetchForecast()` — 启动时自动获取，每日一次
- `loadWeather()` — 按时间范围获取历史天气
- `WeatherInterpolator.interpolate()` — 线性插值填补缺失日期
- `WeatherOverlay` — 图表上的温度覆盖层

### AI 分析
- `triggerAiAnalysis()` → `buildComprehensivePrompt()` → `DeepSeekRepository.analyze()`
- 提示词包含: 数据概览、趋势、异常检测(1.5σ)、天气关联、账单、预测、事件影响

## ChartScreen 组件树
```
Box
├── Column (verticalScroll)
│   ├── ChartTopBar (渐变背景 + 日期范围 + 报告按钮)
│   ├── TimeRangeSelector (周/月/年/全部 滑动胶囊)
│   ├── MeterTypeSelector (电/水/气 切换)
│   ├── ToggleCostButton (kWh/¥ 切换，仅电表)
│   ├── EmptyChartPlaceholder (isEmpty 时替代下方全部面板)
│   └── AnimatedVisibility(renderHeavy, delay 50ms)
│       ├── ElectricAnalysisSection
│       │   ├── HeroKpiRow (3个KpiCard: 总用电/日均/总费用)
│       │   ├── ChartSection → ConsumptionLineChart + WeatherOverlay
│       │   ├── BillBreakdownPanel (峰谷占比条 + 价格 + 水费 + 合计)
│       │   ├── PredictionPanel (进度条 + 三列KPI + 消耗进度 + 预计账单 + 跟踪)
│       │   ├── EventImpactPanel (事件对比柱状图 + AI分析)
│       │   │   └── MarkdownText → BoldAwareLine (AI 分析结果渲染)
│       │   └── AnnotationsList (事件标注时间线)
│       ├── WaterAnalysisSection
│       │   ├── WaterKpiRow
│       │   ├── ChartSection → ConsumptionLineChart (WaterColor)
│       │   ├── WaterBillPanel
│       │   └── WaterPredictionPanel
│       └── GasAnalysisSection
│           ├── GasKpiRow
│           └── ChartSection → ConsumptionLineChart (GasColor)
└── 回到顶部按钮 (scrollState > 400px)
```

另有 `CarbonSummaryCard`（碳足迹卡片，ChartScreen.kt:2594）已定义但当前无调用点（carbonData 已传入 ElectricAnalysisSection 但未渲染）。

## 性能优化
- `renderHeavy` — 延迟 50ms 渲染重面板，避免首帧 JIT 卡顿
- `remember{}` — 图表数据、天气插值、日期列表缓存
- `delay(50)` — LaunchedEffect 中延迟触发

## 已知问题
- ~~ChartScreen 使用 `collectAsState()`~~ 已修复（commit eba3f4b）：现全部使用 `collectAsStateWithLifecycle()`，与 MainScreen 一致
- `hiltViewModel()` 仅是 ChartScreen 的默认参数；AppNavGraph 显式传入已缓存的 `chartVM`，因此应用路径中默认参数不会被调用，也不会重复创建 ViewModel

## 相关文档
- 数据模型: `.claude/docs/data-layer/meter-record.md`
- 计费引擎: `.claude/docs/data-layer/cost-engine.md`
- 预测分析: `.claude/docs/analytics/predictive-analyzer.md`
- 碳足迹: `.claude/docs/analytics/carbon-and-insight.md`
- 外部服务: `.claude/docs/data-layer/external-services.md`
- 主题色: `.claude/docs/ui-layer/theme-and-navigation.md`
- 格式化工具: `.claude/docs/architecture/app-entry-and-di.md` (Formatters)

## 颜色常量 (ChartScreen 使用)
| 常量 | 实际值 | 用途 |
|------|--------|------|
| `ElectricPeakColor` | #FF9922 (StaticPeakColor) | 峰电柱状图 |
| `ElectricValleyColor` | #9977EE (StaticValleyColor) | 谷电柱状图 |
| `NeonYellow` | #00A8FF (ElectricStart 别名) | KPI 强调色 |
| `OutlineDark` | #2A304A | 平电柱状图 |
| `NeonBlue` | #00DDBB (WaterStart 别名) | AI分析按钮 |
| `SuccessGreen` | #00DD99 | 费用/降序 |
| `WarningNeon` | #FFBB33 | 阶梯预警 |
