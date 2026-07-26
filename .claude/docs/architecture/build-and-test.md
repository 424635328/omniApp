# Build & Test Commands

## Build
```bash
# 编译 Android app module
./gradlew :app:compileDebugKotlin

# 编译 KMP shared module (Android target)
./gradlew :shared:compileDebugKotlinAndroid

# 全量编译
./gradlew :app:assembleDebug
```

## Test
```bash
# 运行全部单元测试
./gradlew :app:testDebugUnitTest

# 运行单个测试类
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest"

# 运行 shared module 测试（KMP 目标名为 jvm("desktop")，故任务是 desktopTest 而非 jvmTest）
./gradlew :shared:desktopTest
```

## Test Files
| Module | Test File | Covers |
|--------|-----------|--------|
| app | `AdaptiveClassifierTest.kt` | 自适应阈值学习 |
| app | `AnomalyDetectorTest.kt` | 单调递增 + 突增检测 |
| app | `CostEngineTest.kt` | 计费计算 (峰谷+阶梯) |
| app | `DeepSeekRepositoryTest.kt` | AI 解析降级 |
| app | `EventImpactAnalyzerTest.kt` | 事件标签能耗影响 |
| app | `MeterRepositoryTest.kt` | Repository CRUD + 智能插入 |
| app | `PredictiveAnalyzerTest.kt` | 月度预测 (DES + 天气) |
| app | `SmartInputParserTest.kt` | 正则解析 (全部模式) |
| app | `ThemeDistRepositoryTest.kt` | 每日主题分发 |
| app | `UserPreferencesTest.kt` | DataStore 持久化 |
| app | `WeatherInterpolatorTest.kt` | 天气数据线性插值 |
| app | `WeatherRepositoryTest.kt` | Open-Meteo API 解析 |
| app | `ChartViewModelTest.kt` | 图表数据聚合 |
| shared | `commonTest/.../SharedEnginesTest.kt` | 共享引擎跨平台测试（`:shared:desktopTest` 运行） |

## Environment
- **Android SDK**: `C:/Users/George/AppData/Local/Android/Sdk`
- **JDK**: 17 (AGP requirement)
- **Gradle**: Wrapper (gradlew)
- **Database**: `energy_flow_database` (Room, destructive migration)
