# 能耗手记 · Energy Flow

<p align="center">
  <strong>智能能耗追踪 · 峰谷电费计算 · 天气关联分析 · 月度预测</strong><br/>
  <sub>Jetpack Compose · Material 3 · Hilt · Room · Ktor · Canvas 自绘图表</sub>
</p>

---

## 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [数据库设计](#数据库设计)
- [智能解析器](#智能解析器)
- [自适应分类器](#自适应分类器)
- [计费引擎](#计费引擎)
- [天气插值](#天气插值)
- [异常检测](#异常检测)
- [事件归因分析](#事件归因分析)
- [月度预测](#月度预测)
- [导入时插值](#导入时插值)
- [动画与 UX](#动画与-ux)
- [构建与运行](#构建与运行)
- [测试](#测试)
- [性能优化](#性能优化)
- [依赖清单](#依赖清单)
- [许可证](#许可证)

---

## 项目简介

**能耗手记 (Energy Flow)** 是为个人水电能耗记录而生的 Android App。

核心理念：**你的记录习惯不应该被表单束缚。** 粘贴什么，它就解析什么。

```
7.14 17.17 16776
16.39打开冰箱
7.1 16639 880 两家
6.26下午六点启用冰箱
水0879
```

---

## 核心特性

### 🔌 智能输入 (11 种正则模式)

| 模式 | 示例 | 结果 |
|------|------|------|
| 日期头 | `7.14` | 上下文日期 |
| 日期+时间+数值 | `7.13 01.23 16672` | 电表 16672 |
| 紧凑时间+数值 | `7.13 0123 9310` | 01:23 峰电 |
| 电+水同行 | `7.1 16639 880 两家` | 电 16639 + 水 880 |
| 中文时间 | `6.26下午六点启用冰箱` | 18:00 事件 |
| 时间+备注 | `16.39 打开冰箱` | 纯事件标注 |
| 峰谷配对 | `9310.75` / `7298.66` | 自动峰+谷+总和 |
| 水表前缀 | `水0879` | 水表 879 |
| 纯数值 | `16776` | 自适应分类 |

### 📊 自适应分类器

```
历史数据 → 学习范围 → 动态阈值 → DataStore 缓存
```

阈值随使用自动校准，不再依赖硬编码常量。

### 💰 南京建邺区 2026 现行阶梯电价

**分时电价**（先峰谷、后阶梯）：

| 时段 | 单价 |
|------|------|
| 峰 (8:00-21:00) | 0.5583 元/度 |
| 谷 (0:00-8:00, 21:00-24:00) | 0.3583 元/度 |
| 平 (未开通峰谷) | 0.5283 元/度 |

**阶梯加价**（年累计，按月估算）：

| 档位 | 月上限 | 加价 |
|------|--------|------|
| 一档 | ≤230 度 | 基准价 |
| 二档 | 231-400 度 | +0.05 元/度 |
| 三档 | >400 度 | +0.30 元/度 |

**阶梯水价**（含供水价+水资源税+污水处理费）：

| 档位 | 年上限 | 单价 |
|------|--------|------|
| 一档 | ≤200 吨 | 3.42 元/吨 |
| 二档 | 201-270 吨 | 4.32 元/吨 |
| 三档 | >270 吨 | 7.02 元/吨 |

> 计费规则带版本迁移：应用升级后自动切换为南京最新默认值，也可在设置页自定义。

### 🚨 预保存异常拦截

| 检测 | 触发条件 | 交互 |
|------|---------|------|
| 读数回退 | 新读数 < 上次 | 🔴 标红弹窗 |
| 耗量突增 | 日均 ≥ 历史均值 5× | 🟠 橙色警告 |

### 🌡 天气关联 + 智能插值

- 接入 **Open-Meteo** 免费天气 API（无需 Key）
- 折线图背景叠加温度曲线（最高温/最低温）
- 高温 (>32°C) 区域橙色标注
- **天气线性插值**：缺失日期的温度值自动通过前后已知数据推导
- 点击图表数据点 → Tooltip 同时显示电量和温度范围（H:32° / L:25°）

### 📈 月度预测 + 跟踪

- 基于本月实际日均消耗外推全月
- 进度条动画 + 预计账单
- **预测跟踪**：对比保存时的预测 vs 实际进度，显示偏差

### 📊 kWh ↔ ¥ 一键切换

折线图、KPI 卡片全部跟随切换，直观对比耗量与花费。

### 🔍 事件归因分析

```
"冰箱" 时段日均 8.5 度/天
无标签时段日均 5.2 度/天
差异: +3.3 度/天
```

### ✨ 开屏动画 + 点击跳过

- 多阶段动画：图标弹性入场 + 标题滑入 + 波纹扩散 + 旋转电弧 + 轨道粒子
- **点击任意位置**即可跳过动画，直接进入 App

### 📦 导入时插值

批量导入数据时，自动检测日期缺口并生成插值记录：

```
导入: 7.11 16760, 7.13 16772
自动补入: 7.12 16766 (线性插值)
```

---

## 技术架构

```
┌──────────────────────────────────────────────┐
│              UI Layer (Compose)               │
│  MainScreen · ChartScreen · BillingSettings  │
│  SplashScreen · AnomalyWarningDialog         │
│  ConsumptionLineChart (Canvas)               │
│  WeatherOverlay (Canvas)                     │
└───────────────────┬──────────────────────────┘
                    │ hiltViewModel()
┌───────────────────▼──────────────────────────┐
│             ViewModel Layer                   │
│  MainViewModel · ChartViewModel              │
│  BillingSettingsViewModel                    │
└───────────────────┬──────────────────────────┘
                    │ @Inject
┌───────────────────▼──────────────────────────┐
│            Domain / Data Layer                │
│  MeterRepository · CostEngine               │
│  SmartInputParser · AdaptiveClassifier      │
│  AnomalyDetector · PredictiveAnalyzer       │
│  EventImpactAnalyzer · WeatherRepository    │
│  WeatherInterpolator · ThemeDistRepository  │
└────────┬──────────────────────┬─────────────┘
         │                      │
┌────────▼──────┐    ┌──────────▼──────────┐
│   Room DB     │    │  DataStore          │
│  (SQLite)     │    │  Preferences        │
└───────────────┘    └─────────────────────┘
```

**依赖注入**: Hilt (SingletonComponent + ViewModelComponent)
**持久化**: Room + Preferences DataStore
**网络**: Ktor HttpClient + kotlinx.serialization
**图表**: Compose Canvas 自绘（无第三方图表库）

---

## 项目结构

```
com.example.energyflow/
├── MainActivity.kt                        # @AndroidEntryPoint
├── EnergyFlowApplication.kt              # @HiltAndroidApp
├── di/
│   ├── DatabaseModule.kt                 # Room
│   ├── DataStoreModule.kt                # Preferences DataStore
│   └── NetworkModule.kt                  # Ktor HttpClient
├── data/
│   ├── MeterRecord.kt                    # Room Entity
│   ├── MeterRecordDao.kt                 # Room DAO
│   ├── AppDatabase.kt                    # Room Database
│   ├── Converters.kt                     # 类型转换
│   ├── MeterRepository.kt               # 数据仓库
│   ├── SmartInputParser.kt              # 11 种解析模式
│   ├── ClassificationThresholds.kt      # 阈值数据类
│   ├── AdaptiveClassifier.kt            # 自适应学习
│   ├── AnomalyDetector.kt               # 异常检测
│   ├── CostEngine.kt                    # 南京阶梯电价引擎
│   ├── PredictiveAnalyzer.kt            # 月度预测
│   ├── EventImpactAnalyzer.kt           # 事件归因
│   ├── WeatherRepository.kt             # Open-Meteo API
│   ├── WeatherInterpolator.kt           # 温度线性插值
│   ├── ThemeDistRepository.kt           # 每日主题
│   ├── ThemeDistResponse.kt             # 主题 API 响应
│   └── UserPreferences.kt              # DataStore (带版本迁移)
├── ui/
│   ├── SplashScreen.kt                  # 开屏动画
│   ├── MainScreen.kt                    # 时间轴主页
│   ├── MainViewModel.kt                 # 主页 ViewModel
│   ├── TimelineItem.kt                  # 时间轴卡片
│   ├── chart/
│   │   ├── ChartScreen.kt               # 分析页
│   │   ├── ChartViewModel.kt            # 图表 ViewModel
│   │   ├── ConsumptionLineChart.kt      # Canvas 折线图
│   │   └── WeatherOverlay.kt            # 温度曲线叠加层
│   ├── settings/
│   │   ├── BillingSettingsScreen.kt     # 计费设置
│   │   └── BillingSettingsViewModel.kt
│   ├── components/
│   │   ├── AddRecordSheet.kt            # 添加记录
│   │   ├── EditRecordSheet.kt           # 编辑记录
│   │   └── BatchImportSheet.kt          # 批量导入
│   ├── navigation/
│   │   └── AppNavGraph.kt               # 三 Tab 导航
│   ├── theme/
│   │   ├── Color.kt                     # 霓虹色板
│   │   ├── Theme.kt                     # 暗黑主题 + HSL 色相偏移
│   │   └── Type.kt                      # 等宽字体
│   └── utils/
│       └── Formatters.kt                # 格式化工具
└── test/
    └── CostEngineTest.kt                # 计费引擎测试
```

---

## 数据库设计

### MeterRecord (Room Entity)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| timestamp | LocalDateTime | 记录时间 |
| isElectricRecorded | Boolean | 含电表读数 |
| electricTotal | Double? | 总电量 (kWh) |
| electricPeak | Double? | 峰电量 |
| electricValley | Double? | 谷电量 |
| isWaterRecorded | Boolean | 含水表读数 |
| waterTotal | Double? | 水表读数 (m³) |
| note | String? | 备注/事件标签 |

---

## 智能解析器

SmartInputParser 采用**上下文关联**解析，逐行处理：

1. `MM.DD` → 日期头
2. `MM.DD HH.MM VALUE [NOTE]` → 完整记录
3. `MM.DD HHMM VALUE [NOTE]` → 紧凑时间
4. `MM.DD VALUE1 VALUE2 [NOTE]` → 电+水
5. `MM.DD 中文时间 [NOTE]` → 中文时间
6. `MM.DD HH.MM NOTE` → 时间+备注
7. `HH.MM VALUE [NOTE]` → 时间+数值 (需上下文日期)
8. `HHMM VALUE [NOTE]` → 紧凑时间 (需上下文)
9. `HH.MM NOTE` → 时间+备注 (需上下文)
10. `水VALUE` → 水表前缀
11. `VALUE` → 纯数值 (自适应分类 + 峰谷配对)

---

## 自适应分类器

| 阈值 | 默认值 | 学习策略 |
|------|--------|---------|
| totalElectricMin | 15000 | avg(历史总电) × 0.85 |
| peakMin / peakMax | 9000 / 10000 | avg(历史峰电) × 0.85 / 1.15 |
| valleyMin / valleyMax | 7000 / 8000 | avg(历史谷电) × 0.85 / 1.15 |
| waterMax | 1000 | max(历史水表) × 1.2 |

---

## 计费引擎

南京建邺区 2026 现行标准，**先峰谷、后阶梯**：

```
有效电价 = 基础分时电价 + (二档加价 × 二档电量占比 + 三档加价 × 三档电量占比) / 总电量

电费 = 峰电量 × 有效峰价 + 谷电量 × 有效谷价 + 平电量 × 有效平价
水费 = 一档水量 × 3.42 + 二档水量 × 4.32 + 三档水量 × 7.02
```

带版本迁移：旧用户自动切换为南京默认值，也可随时在设置页自定义。

---

## 天气插值

`WeatherInterpolator` 使用**线性插值**填补天气数据缺口：

```
已知: 7.10 最高 30°, 7.14 最高 38°
目标: 7.12
f = (7.12 - 7.10) / (7.14 - 7.10) = 0.5
7.12 最高 = 30 + (38 - 30) × 0.5 = 34°
```

- 双向有数据 → 线性插值
- 单向有数据 → 最近邻外推
- 已有数据 → 直接使用

---

## 异常检测

| 类型 | 触发条件 | 行为 |
|------|---------|------|
| 读数回退 | 新读数 < 历史记录 | 🔴 弹窗拦截 → 确认/返回 |
| 耗量突增 | 区间日均 ≥ 历史均值 500% | 🟠 橙色警告 |

---

## 事件归因分析

备注中的标签关键词（"冰箱"、"空调"等）→ 自动对比有/无该标签时段的日均耗电差。

---

## 月度预测

```
日均 = (最新 - 最早) / 实际天数
预计全月 = 本月已耗 + 日均 × 剩余天数
预计账单 = CostEngine.calculate(预计全月)
```

预测快照自动保存，后续可对比预测 vs 实际偏差。

---

## 导入时插值

批量导入时自动检测相邻记录间的日期缺口：

```
电表间隔 ≥ 2 天 → 逐日生成插值记录
水表同理，独立插值
```

插值记录时间戳为 12:00，不附带备注。

---

## 动画与 UX

| 功能 | 实现 |
|------|------|
| 开屏动画 | 5 阶段：图标弹性入场 + 标题滑入 + 波纹/电弧/粒子 Canvas 动效 |
| 跳过开屏 | 任意位置点击 → 150ms 淡出 |
| 图表绘制 | Animatable 从左到右渐进绘制 (700ms) |
| KPI 卡片 | animateFloatAsState 数字过渡 |
| 进度条 | animateFloatAsState 平滑填充 |
| 卡片按压 | InteractionSource → 缩放 0.97x + 阴影变化 + 发光条 |
| 底部导航 | 选中图标 Spring 弹性缩放 1.18x |
| FAB 按压 | InteractionSource → 缩放 + 阴影 |
| 空状态 | infiniteRepeatable 呼吸动画 |
| 行内操作 | 点击展开编辑/删除，AnimatedVisibility |
| 返回键 | BackHandler 优先关闭底部表单 |

---

## 构建与运行

### 环境

- Android Studio 2024+
- JDK 17+
- Gradle 8.13 · Kotlin 2.1.20
- compileSdk 36 · minSdk 34 · targetSdk 36

### 命令

```bash
# Debug
./gradlew assembleDebug

# Release (R8 混淆 + 资源压缩)
./gradlew assembleRelease

# 测试
./gradlew testDebugUnitTest

# 安装
./gradlew installDebug
```

### 天气 API

使用 **Open-Meteo** 免费 API，无需注册 Key，开箱即用。

---

## 测试

```
BUILD SUCCESSFUL — 4 test classes, all passed
```

| 类别 | 覆盖 |
|------|------|
| CostEngine | 4 用例 — 阶梯加价 / 三档触发 / 一档内 / 水费 |
| SmartInputParser | 39 用例 — 11 种模式 / 峰谷配对 / 极端输入 |

---

## 性能优化

### R8 混淆 + 资源压缩

```
Release: isMinifyEnabled = true + isShrinkResources = true
ProGuard: Room / Hilt / Ktor / kotlinx.serialization / Compose keep rules
```

### 构建加速

```
gradle.properties:
  org.gradle.parallel=true
  org.gradle.caching=true
  org.gradle.configureondemand=true
```

### 运行时优化

| 措施 | 效果 |
|------|------|
| Baseline Profiles (profileinstaller) | 冷启动 ~30% 加速 |
| R8 dex-startup-optimization | 启动 DEX 布局优化 |
| Canvas 点稀疏化 (120 点抽 1) | 大数据量不卡顿 |
| LazyColumn + animateItem + key | 列表高效渲染 |
| DataStore 缓存分类阈值 | 避免启动重算 |
| WeatherInterpolator 缓存 (remember) | 避免重复插值 |
| 天气每日仅请求一次 (Open-Meteo 限流) | 减少网络请求 |

---

## 依赖清单

| 类别 | 依赖 | 用途 |
|------|------|------|
| UI | Compose BOM + Material 3 + Icons | 全 Compose UI |
| 导航 | Navigation Compose | 三 Tab 导航 |
| 数据 | Room + KSP | 本地 SQLite |
| 偏好 | DataStore Preferences | 计费规则/主题/阈值 |
| DI | Hilt + KSP | 依赖注入 |
| 网络 | Ktor Client (Android) | Open-Meteo API |
| 序列化 | kotlinx.serialization | JSON 解析 |
| 性能 | Profile Installer | ART 预编译 |
| 测试 | JUnit 4 | 单元测试 |

---

## 许可证

MIT © 2026 Energy Flow Contributors
