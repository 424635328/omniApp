# EnergyFlow Architecture Overview

## Project Identity
EnergyFlow is a **家庭能耗记录 Android App**，用于记录电表/水表/燃气表读数，计算阶梯电价费用，预测月度用电，分析碳足迹。

## Tech Stack
- **Language**: Kotlin (Android) + Kotlin Multiplatform (shared module)
- **UI**: Jetpack Compose (Material 3, dark-first theme)
- **DI**: Hilt (`@Singleton`, `@HiltViewModel`, `@Inject`)
- **Persistence**: Room (SQLite) + DataStore (Preferences)
- **Networking**: Ktor HttpClient (Open-Meteo weather, DeepSeek AI)
- **Serialization**: kotlinx.serialization
- **DateTime**: java.time (Android) + kotlinx.datetime (shared KMP)
- **Navigation**: Custom tab switch (no NavHost), AnimatedContent transitions

## Module Structure
```
omniAPP/
├── app/                          # Android application module
│   └── src/main/java/com/example/energyflow/
│       ├── EnergyFlowApplication.kt  # @HiltAndroidApp
│       ├── MainActivity.kt           # 入口 Activity (主题/闪屏/引导/深链)
│       ├── data/                     # 数据层 (Repository, Engine, Parser, Detector)
│       ├── di/                       # Hilt DI modules (Database, DataStore, Network)
│       └── ui/                       # UI层 (Screen, ViewModel, Theme, Components)
├── shared/                        # KMP shared module
│   └── src/commonMain/kotlin/com/example/energyflow/shared/
│       ├── CostEngine.kt          # 计费计算 (纯逻辑)
│       ├── PredictiveAnalyzer.kt  # 预测分析 (DES + 天气)
│       ├── AnomalyDetector.kt     # 异常检测
│       ├── CarbonFootprint.kt     # 碳足迹计算
│       └── WrappedReport.kt       # 年度报告
└── baselineprofile/               # Baseline Profile generator
```

## Key Design Patterns
1. **Hilt Wrapper Pattern**: Android 层的 `CostEngine`, `PredictiveAnalyzer`, `CarbonFootprint` 都是 Hilt @Singleton 包装器，实际逻辑委托给 `shared` 模块的纯逻辑对象
2. **Adaptive Learning**: `AdaptiveClassifier` 从用户历史数据自动学习分类阈值，缓存到 DataStore
3. **Smart Parse + AI Fallback**: `SmartInputParser` 正则优先，失败后降级到 `DeepSeekRepository.parseNaturalInput()`
4. **Anomaly Gate**: `AnomalyDetector` 在保存前校验单调递增 + 突增检测，弹出确认对话框
5. **Tab Instant Switch**: ViewModel 常驻内存，标签页通过 `AnimatedContent` 切换，无 NavHost 开销

## Data Flow
```
User Input → SmartInputParser → AnomalyDetector → MeterRepository → Room
                                      ↓ (warning)
                              AnomalyWarningDialog → force/cancel
                                                      ↓
InsightGenerator ← MeterRepository ← Room → ChartViewModel → ChartScreen
PredictiveAnalyzer ← MeterRepository ← Room → MainViewModel → MainScreen
CostEngine ← UserPreferences.billingRules ← DataStore
```

## File Reference Map
| Concern | Android Entry | Shared Logic |
|---------|--------------|-------------|
| 计费 | `data/CostEngine.kt` | `shared/CostEngine.kt` (CostEngineShared) |
| 预测 | `data/PredictiveAnalyzer.kt` | `shared/PredictiveAnalyzer.kt` (PredictiveAnalyzerShared) |
| 异常检测 | `data/AnomalyDetector.kt` | `shared/AnomalyDetector.kt` (AnomalyDetectorShared) |
| 碳足迹 | `data/CarbonFootprint.kt` | `shared/CarbonFootprint.kt` (CarbonCalculator) |
| 年度报告 | `ui/WrappedViewModel.kt` | `shared/WrappedReport.kt` (WrappedReportBuilder) |
| 输入解析 | `data/SmartInputParser.kt` | (Android only, regex-heavy) |
| 自适应阈值 | `data/AdaptiveClassifier.kt` | (Android only) |
| 天气 | `data/WeatherRepository.kt` | (Android only, Ktor) |
| AI分析 | `data/DeepSeekRepository.kt` | (Android only, Ktor) |
| 图表分析 | `ui/chart/ChartViewModel.kt` | (Android only, ~1070 lines) |
| 账单报告 | `data/BillReportGenerator.kt` | (Android only, text+HTML) |
| 每日主题 | `data/ThemeDistRepository.kt` | (Android only, Ktor) |
| OCR扫表 | `data/OcrSmartProcessor.kt` | (Android only) |
| 桌面部件 | `widget/EnergyFlowWidget.kt` | (Android only, Glance) |

## ChartViewModel Data Flow
```
electricRecords ──→ recalculateAnalytics()
  ├── CostEngine.calculateBill() → BillData
  ├── CarbonFootprint.calculate() → CarbonResult
  ├── PredictiveAnalyzer.predictMonth() → MonthPrediction
  │     └── 预测账单 + 快照 + 跟踪
  ├── EventImpactAnalyzer → List<EventImpact>
  ├── computeForecastConsumptions() → 虚线投影点
  └── updateChartData() → ChartData

waterRecords ──→ recalculateWaterAnalytics()
  ├── CostEngine (水费) → WaterBillData
  └── predictWaterMonth() → MonthPrediction

gasRecords ──→ recalculateGasAnalytics()
  └── calculateGasDailyConsumptions() → ChartData
```
