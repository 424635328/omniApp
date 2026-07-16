# 能耗手记 · Energy Flow

<p align="center">
  <strong>智能能耗追踪 · 峰谷电费计算 · 天气关联分析 · 月度预测</strong><br/>
  <sub>Jetpack Compose · Material 3 · Hilt · Room · Ktor · CameraX · Canvas 自绘图表</sub>
</p>

---

## 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
- [使用示例](#使用示例)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [数据库设计](#数据库设计)
- [智能解析器](#智能解析器)
- [自适应分类器](#自适应分类器)
- [计费引擎](#计费引擎)
- [天气与插值](#天气与插值)
- [异常检测](#异常检测)
- [事件归因分析](#事件归因分析)
- [本地洞察引擎](#本地洞察引擎)
- [DeepSeek AI 分析](#deepseek-ai-分析)
- [月度预测与跟踪](#月度预测与跟踪)
- [账单报告分享](#账单报告分享)
- [拍照扫描 (OCR)](#拍照扫描-ocr)
- [主屏幕小部件](#主屏幕小部件)
- [快捷设置磁贴](#快捷设置磁贴)
- [开屏动画](#开屏动画)
- [新手引导](#新手引导)
- [每日主题分布](#每日主题分布)
- [构建与运行](#构建与运行)
- [测试](#测试)
- [性能优化](#性能优化)
- [依赖清单](#依赖清单)
- [许可证](#许可证)

---

## 项目简介

**能耗手记 (Energy Flow)** 是为个人家庭能耗记录而生的 Android App。

核心理念：**你的记录习惯不应该被表单束缚。** 粘贴什么，它就解析什么。无论是手写笔记、微信消息还是拍照识别，都能一键导入。

```
6.26
12.00 16609.41
水879
18.00 开始启用冰箱
6.27
水879.20
12.00 16615.21
6.28
水879.40
12.00 16621
17.06 太吵了停止使用冰箱
```

App 覆盖从**智能录入 → 阶梯计费 → 趋势分析 → 报表分享 → 拍照识别 → 桌面小组件**的完整闭环。

---

## 核心特性

### 🔌 智能输入（17+ 解析模式）

| 模式 | 示例 | 结果 |
|------|------|------|
| 日期头 | `7.14` | 设置上下文日期 |
| 日期+时间+数值 | `7.13 01.23 16672` | 电表读数 |
| 紧凑时间+数值 | `7.13 0123 9310` | 01:23 峰电读数 |
| 电+水同行 | `7.1 16639 880 两家` | 电 + 水同一条 |
| 电+水+气同行 | `7.15 电16788 水880 气123` | 三表同行 |
| 中文时间 | `6.26下午六点启用冰箱` | 18:00 事件记录 |
| 时间+备注 | `16.39 打开冰箱` | 纯事件标注 |
| 峰谷配对 | `9310.75` / `7298.66` | 自动峰+谷+总和 |
| 水表前缀 | `水0879` | 水表 879 |
| 气表前缀 | `气0123` | 燃气表 123 |
| 纯数值 | `16776` | 自适应分类 → 电/水/气 |
| 前缀读写 | `电16788 水880` | 显式指定类型 |
| 峰谷显式 | `峰9310 谷7298` | 峰谷分开录入 |

### ⚡💧🔥 三表合一

电表、水表、燃气表三条独立数据流，在表单中通过开关独立启停：

- 每条记录可同时记录任意组合的电/水/燃气读数
- 上次读数一键「沿用」+ 自动日期 +1 天
- 时间线卡片清晰展示各表读数和间隔消耗量
- **筛选栏**：一键筛选电/水/燃气/含备注的记录，显示各类型计数

### 📸 拍照扫描（CameraX + ML Kit）

- 调用系统相机拍摄电表/水表照片
- **ML Kit 中文文本识别**自动提取读数
- **OcrSmartProcessor** 启发式清洗引擎：滤除条码、日期、编号等噪音
- 识别结果自动填入添加表单，一键确认

### 💰 阶梯计费（南京建邺区 2026 现行）

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

**阶梯水价**：

| 档位 | 年上限 | 单价 |
|------|--------|------|
| 一档 | ≤200 吨 | 3.42 元/吨 |
| 二档 | 201-270 吨 | 4.32 元/吨 |
| 三档 | >270 吨 | 7.02 元/吨 |

> 规则带版本迁移，升级后自动切换为南京最新默认值，也可在设置页自定义全部参数。

### 📊 自适应分类器

历史数据 → 学习范围 → 动态阈值 → DataStore 缓存。阈值随使用自动校准，不再依赖硬编码常量。

### 🚨 预保存异常拦截

| 检测 | 触发条件 | 交互 |
|------|---------|------|
| 读数回退 | 新读数 < 上次 | 🔴 弹窗拦截 |
| 耗量突增 | 日均 ≥ 历史均值 5× | 🟠 橙色警告 |

### 🌡 天气关联

- 接入 **Open-Meteo** 免费天气 API（无需 Key）
- 折线图背景叠加温度曲线（最高温/最低温）
- 高温 (>32°C) 区域橙色标注
- **天气插值**：缺失日期自动线性推导
- 点击数据点 Tooltip 显示电量和温度范围

### 📈 月度预测 + 跟踪

- 基于本月实际日均消耗外推全月
- 进度条动画 + 预计账单
- 预测快照自动保存，后续对比预测 vs 实际偏差

### 📊 kWh ↔ ¥ 一键切换

折线图、KPI 卡片全部跟随切换，直观对比耗量与花费。

### 🔍 事件归因分析

```
"冰箱" 时段日均 8.5 度/天
无标签时段日均 5.2 度/天
差异: +3.3 度/天
```

### 💡 本地洞察引擎（无需 API）

基于历史数据和天气模式的启发式实时洞察：

| 洞察类型 | 触发条件 | 示例 |
|---------|---------|------|
| ⚠️ 阶梯预警 | 本月用电超二档阈值 80% | "距二档加价仅剩 XX 度" |
| 🌡️ 高温影响 | 近 3 天高温 + 用电飙升 | "日均用电从 X 飙升至 Y" |
| ⚡ 谷电偏低 | 近 7 天谷电占比 < 20% | "建议将洗衣机调至谷时段" |
| 📅 周末偏高 | 周末日均 > 工作日 1.3× | "周末高于工作日 X%" |

### 🤖 DeepSeek AI 全局分析

配置 API Key 后，在分析页点击「AI 分析」按钮，获得基于完整数据集的多维度洞察（趋势、异常、事件影响、费用预测）。

同时支持**自然语言降级解析**：当正则匹配失败时，自动调用 DeepSeek 理解模糊输入。

### 📋 账单报告分享

- 生成结构化月度账单报告（电费/水费/燃气费分项 + 峰谷比例条 + 阶梯信息）
- 通过系统分享面板分享到微信、邮件等
- 不依赖 API Key，始终可用

### 📱 主屏幕小部件（Glance）

- 桌面小部件显示本月用电量、费用
- 点击打开 App
- 数据自动刷新（添加/编辑记录后同步更新）

### ⚡ 快捷设置磁贴

下拉通知栏快速查看本月用电 + 费用概览，点击进入 App。

### 🎓 新手引导

首次启动 3 页引导：智能输入介绍 / 三表合一 / 分析与计费。支持跳过。

### ✨ 开屏动画

多阶段动画：图标弹性入场 + 标题滑入 + 波纹扩散 + 旋转电弧 + 轨道粒子，点击任意位置跳过。

### 🎨 每日主题（ThemeDist）

每天自动切换渐变色主题（HSL 色相偏移），启用后折线图、KPI 卡片配色每日不同。

---

## 使用示例

随手记录日常能耗数据，App 自动解析：

```
7.1
12.00 16639 880 两家
7.2
09.54 16647
18.34 16653
7.3
水880.30
12.00 16660.50
7.4
17.14 16668
7.5
12.00 16677.75 空调
```

> 纯文本批量粘贴 → 自动识别日期、时间、电表/水表/燃气表读数、备注标签。

---

## 技术架构

```
┌──────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                  │
│  MainScreen · ChartScreen · BillingSettingsScreen    │
│  SplashScreen · OnboardingScreen · ScanScreen        │
│  ConsumptionLineChart (Canvas) · WeatherOverlay       │
│  ModalBottomSheet · SwipeToDismissBox · TimelineItem │
│  EnergyFlowWidget (Glance) · EnergyTileService       │
└─────────────────────┬────────────────────────────────┘
                      │ hiltViewModel()
┌─────────────────────▼────────────────────────────────┐
│                  ViewModel Layer                       │
│  MainViewModel · ChartViewModel                       │
│  BillingSettingsViewModel                             │
└─────────────────────┬────────────────────────────────┘
                      │ @Inject
┌─────────────────────▼────────────────────────────────┐
│               Domain / Data Layer                      │
│  MeterRepository · CostEngine · BillReportGenerator  │
│  SmartInputParser · OcrSmartProcessor                │
│  AdaptiveClassifier · AnomalyDetector                │
│  PredictiveAnalyzer · EventImpactAnalyzer            │
│  InsightGenerator · WeatherRepository                │
│  WeatherInterpolator · DeepSeekRepository            │
│  ThemeDistRepository                                 │
└────────┬──────────────────────────────┬──────────────┘
         │                              │
┌────────▼──────┐            ┌──────────▼──────────┐
│   Room DB     │            │  DataStore           │
│  (SQLite)     │            │  Preferences         │
└───────────────┘            └─────────────────────┘
```

**依赖注入**: Hilt (SingletonComponent + ViewModelComponent)
**持久化**: Room + Preferences DataStore
**网络**: Ktor HttpClient + kotlinx.serialization
**图表**: Compose Canvas 自绘（无第三方图表库）
**相机**: CameraX + ML Kit Text Recognition
**小部件**: Glance (App Widget)

---

## 项目结构

```
com.example.energyflow/
├── MainActivity.kt                    # @AndroidEntryPoint
├── EnergyFlowApplication.kt          # @HiltAndroidApp
├── di/
│   ├── DatabaseModule.kt             # Room 数据库
│   ├── DataStoreModule.kt            # Preferences DataStore
│   └── NetworkModule.kt              # Ktor HttpClient
├── data/
│   ├── MeterRecord.kt                # Room Entity（电/水/燃气三表）
│   ├── MeterRecordDao.kt             # Room DAO
│   ├── AppDatabase.kt                # Room Database
│   ├── Converters.kt                 # 类型转换器
│   ├── MeterRepository.kt            # 数据仓库（含插值 + 去重）
│   ├── SmartInputParser.kt           # 17+ 种解析模式
│   ├── OcrSmartProcessor.kt          # OCR 文本清洗引擎
│   ├── ClassificationThresholds.kt   # 自适应阈值数据类
│   ├── AdaptiveClassifier.kt         # 自适应分类器
│   ├── AnomalyDetector.kt            # 回退/突增异常检测
│   ├── CostEngine.kt                 # 计费引擎（阶梯 + 峰谷）
│   ├── BillingRules.kt              # 计费规则数据类
│   ├── BillReportGenerator.kt        # 月度账单报告生成
│   ├── PredictiveAnalyzer.kt         # 月度预测
│   ├── EventImpactAnalyzer.kt        # 事件归因分析
│   ├── InsightGenerator.kt           # 本地启发式洞察
│   ├── DeepSeekRepository.kt         # DeepSeek API 客户端
│   ├── WeatherRepository.kt          # Open-Meteo 天气 API
│   ├── WeatherInterpolator.kt        # 温度线性插值
│   ├── ThemeDistRepository.kt        # 每日主题
│   ├── ThemeDistResponse.kt          # 主题 API 响应
│   └── UserPreferences.kt           # DataStore（带版本迁移）
├── ui/
│   ├── SplashScreen.kt               # 多阶段开屏动画
│   ├── OnboardingScreen.kt           # 3 页新手引导
│   ├── MainScreen.kt                 # 时间轴 + 筛选栏 + BottomSheet
│   ├── MainViewModel.kt              # 主页 ViewModel
│   ├── TimelineItem.kt               # 时间轴卡片（滑动删除 + 触觉反馈）
│   ├── camera/
│   │   └── ScanScreen.kt            # CameraX 拍照 + ML Kit OCR
│   ├── chart/
│   │   ├── ChartScreen.kt           # 分析页
│   │   ├── ChartViewModel.kt        # 图表 ViewModel
│   │   ├── ConsumptionLineChart.kt  # Canvas 折线图
│   │   └── WeatherOverlay.kt        # 温度曲线叠加层
│   ├── settings/
│   │   ├── BillingSettingsScreen.kt # 设置页（计费/主题/数据/账单分享）
│   │   └── BillingSettingsViewModel.kt
│   ├── components/
│   │   ├── AddRecordSheet.kt        # 添加记录
│   │   ├── EditRecordSheet.kt       # 编辑记录
│   │   └── BatchImportSheet.kt      # 批量导入
│   ├── navigation/
│   │   └── AppNavGraph.kt           # 三 Tab 导航（交叉淡入淡出动画）
│   ├── theme/
│   │   ├── Color.kt                 # 霓虹色板 + 渐变定义
│   │   ├── Theme.kt                 # 暗黑主题 + HSL 色相偏移
│   │   └── Type.kt                  # 等宽字体
│   ├── tile/
│   │   └── EnergyTileService.kt     # 快捷设置磁贴
│   └── utils/
│       └── Formatters.kt            # 格式化工具
├── widget/
│   ├── EnergyFlowWidget.kt          # Glance 桌面小部件
│   └── EnergyWidgetReceiver.kt      # 小部件数据更新接收器
└── test/                            # 14 个测试类
    ├── data/
    │   ├── CostEngineTest.kt
    │   ├── SmartInputParserTest.kt
    │   ├── AdaptiveClassifierTest.kt
    │   ├── AnomalyDetectorTest.kt
    │   ├── PredictiveAnalyzerTest.kt
    │   ├── EventImpactAnalyzerTest.kt
    │   ├── DeepSeekRepositoryTest.kt
    │   ├── MeterRepositoryTest.kt
    │   ├── WeatherInterpolatorTest.kt
    │   ├── WeatherRepositoryTest.kt
    │   ├── ThemeDistRepositoryTest.kt
    │   └── UserPreferencesTest.kt
    └── ui/
        └── chart/
            └── ChartViewModelTest.kt
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
| isGasRecorded | Boolean | 含燃气表读数 |
| gasTotal | Double? | 燃气读数 (m³) |
| note | String? | 备注/事件标签 |

---

## 智能解析器

SmartInputParser 采用**上下文关联 + 峰谷暂挂配对**的逐行解析策略：

```
日期头 → 更新上下文 (月, 日)
完整行 → 解析并更新上下文
无日期行 → 复用上下文日期
峰谷暂挂 → 等待配对后合并为一条记录
```

### 解析模式

| # | 模式 | 正则 | 示例 | 结果 |
|---|------|------|------|------|
| 1 | 日期头 | `MM.DD` | `7.14` | 更新上下文日期 |
| 2 | 完整记录 | `MM.DD HH.MM VALUE [NOTE]` | `7.13 01.23 16672` | 电表 |
| 3 | 紧凑时间 | `MM.DD HHMM VALUE [NOTE]` | `7.13 0123 9310` | 01:23 读数 |
| 4 | 电+水 | `MM.DD V1 V2 [NOTE]` | `7.1 16639 880 两家` | 电 16639 + 水 880 |
| 5 | 中文时间 | `MM.DD 中文时间 NOTE` | `6.26下午六点启用冰箱` | 18:00 事件 |
| 6 | 中文数字 | `MM.DD 三点二十五分` | `7.3 下午三点二十五分` | 15:25 |
| 7 | 时间+备注 | `MM.DD HH.MM NOTE` | `6.26 16.39 打开冰箱` | 纯事件 |
| 8 | 前缀读写 | `MM.DD 电VALUE 水VALUE 气VALUE 峰VALUE 谷VALUE` | `7.15 电16788 水880` | 显式类型 |
| 9 | 电 + 时间 | `MM.DD VALUE VALUE NOTE` | `7.5 16677 12.00 空调` | 电 + 备注 |
| 10 | 数值 + 日期 | `MM.DD VALUE NOTE` | `7.5 16677.75 空调` | 电 + 标签 |
| 11 | 紧凑时间 | `HHMM VALUE [NOTE]` | `0123 16672` | 需上下文日期 |
| 12 | 时间+备注 | `HH.MM NOTE` | `16.39 打开冰箱` | 需上下文日期 |
| 13 | 水表前缀 | `MM.DD 水VALUE [NOTE]` | `7.3 水880.30` | 水表 |
| 14 | 气表前缀 | `MM.DD 气VALUE [NOTE]` | `7.15 气123.45` | 燃气表 |
| 15 | 纯水表 | `水VALUE` | `水879` | 水表（需日期上下文） |
| 16 | 纯气表 | `气VALUE` | `气123` | 燃气表（需日期上下文） |
| 17 | 纯数值 | `VALUE` | `16776` | 自适应分类 + 峰谷配对 |
| — | 峰谷暂挂 | 数值在 4000-9000 → 自动峰谷配对 | `9310` + `7298` | 峰 9310 + 谷 7298 |

---

## 自适应分类器

| 阈值 | 默认值 | 学习策略 |
|------|--------|---------|
| totalElectricMin | 15000 | avg(历史总电) × 0.85 |
| peakMin | 8000 | avg(历史峰电) × 0.85 |
| peakMax | 10000 | avg(历史峰电) × 1.15 |
| valleyMin | 6000 | avg(历史谷电) × 0.85 |
| valleyMax | 8000 | avg(历史谷电) × 1.15 |
| waterMax | 1000 | max(历史水表) × 1.2 |
| gasMax | 500 | max(历史燃气) × 1.2 |

---

## 计费引擎

南京建邺区 2026 现行标准，**先峰谷、后阶梯**：

```
有效电价 = 基础分时电价 + (二档加价 × 二档电量占比 + 三档加价 × 三档电量占比) / 总电量

电费 = 峰电量 × 有效峰价 + 谷电量 × 有效谷价 + 平电量 × 有效平价
水费 = 一档水量 × 3.42 + 二档水量 × 4.32 + 三档水量 × 7.02
```

带版本迁移：旧用户自动切换为南京默认值，所有参数可在设置页自定义。

---

## 天气与插值

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
- 每日仅请求一次（Open-Meteo 限流）

---

## 异常检测

| 类型 | 触发条件 | 行为 |
|------|---------|------|
| 读数回退 | 新读数 < 历史记录 | 🔴 弹窗拦截 → 确认/返回 |
| 耗量突增 | 区间日均 ≥ 历史均值 500% | 🟠 橙色警告 |
| 批量回退 | 导入数据中某读数低于历史记录 | 逐条提示 |

---

## 事件归因分析

备注中的标签关键词（"冰箱"、"空调"等）→ 自动对比有/无该标签时段的日均耗电差。支持多标签独立计算。

---

## 本地洞察引擎

`InsightGenerator` 基于本地数据和天气模式，零网络依赖，实时生成 4 类洞察：

| 类型 | 触发条件 | 示例输出 |
|------|---------|---------|
| ⚠️ 阶梯预警 | 本月用电超二档阈值 80% | "距二档加价仅剩 45 度" |
| 🌡️ 高温影响 | 近 3 天高温 > 35°C + 用电飙升 > 1.5× | "日均用电从 5.2→8.5 度" |
| ⚡ 谷电偏低 | 近 7 天谷电占比 < 20% | "建议将洗衣机调至谷时段" |
| 📅 周末偏高 | 周末日均 > 工作日 1.3× | "周末高于工作日 40%" |

洞察按严重程度分级：`INFO` / `WARNING` / `CRITICAL`，分析页顶部实时展示。

---

## DeepSeek AI 分析

`DeepSeekRepository` 封装 DeepSeek Chat API（`deepseek-chat` 模型 v4 flash）：

- **全局分析**：分析页点击「🤖 AI 分析」获取多维度洞察（趋势、异常、天气关联、事件影响、费用预测）
- **自然语言降级**：当正则解析失败时，调用 DeepSeek 理解自然语言输入（"上周五看了电表16780" → 提取结构化数据）
- 需在设置页填入 API Key，[platform.deepseek.com](https://platform.deepseek.com) 获取

---

## 月度预测与跟踪

```
日均 = (最新 - 最早) / 实际天数
预计全月 = 本月已耗 + 日均 × 剩余天数
预计账单 = CostEngine.calculate(预计全月)
```

预测快照自动保存到 DataStore，后续可对比预测 vs 实际偏差（跟踪模式）。

---

## 账单报告分享

`BillReportGenerator` 生成结构化月度账单文本，直接通过系统分享面板发送：

```
══════════════════════════════════════
   ⚡ 能耗手记 · 月度账单
   7月1日 — 7月15日  (14天)
══════════════════════════════════════

  💰 费用总览
──────────────────────────────────────
    电费      ¥152.83    (61%)
    水费      ¥63.27     (25%)
    燃气费    ¥33.60     (13%)
    合计      ¥249.70

  📊 用电明细
──────────────────────────────────────
    总用电    285.0 kWh     日均 19.0 kWh
    峰电 180.0 ████████████░░░░ 63%
    谷电 105.0 ███████░░░░░░░░░ 37%
    阶梯     二档
```

- 使用用户自定义的计费规则（非硬编码）
- 费用占比 + 峰谷视觉比例条
- 不依赖 API Key，始终可用
- 设置页 → 账单管理 → 分享月度账单

---

## 拍照扫描 (OCR)

`ScanScreen` 调用 CameraX + ML Kit 实现电表/水表拍照识别：

1. 打开相机，对准电表/水表
2. 拍照获取 `ImageProxy`
3. **ML Kit Chinese Text Recognition** 识别中文文本
4. **OcrSmartProcessor** 清洗过滤：滤除条码、日期时间、编号等噪音
5. 提取读数自动填入添加记录表单，用户确认后保存

OCR 清洗规则：
- `O`→`0`，`l/I`→`1` 数字纠错
- 过滤纯长整数（条形码）、日期格式、含冒号行
- 按"电/kWh/度"和"水/m³/吨"上下文自动分类
- 无上下文时取最大浮点数作为读数

---

## 主屏幕小部件

基于 **Glance** 框架的桌面小部件：

- 显示：本月用电量 + 当月电费
- 显示账单月份标签
- 点击打开 App 主页
- 使用 **PreferencesGlanceStateDefinition** 持久化数据
- 通过 `EnergyWidgetReceiver` 监听更新事件

### 小部件状态数据

| Key | 类型 | 说明 |
|-----|------|------|
| KEY_COST | Double | 当月总电费 |
| KEY_KWH | Double | 当月总用电量 |
| KEY_MONTH | String | 月份标签 ("2026年7月") |

---

## 快捷设置磁贴

`EnergyTileService`（Android 快捷设置面板 Tile）：

- 显示：本月用电量和费用概览
- 数据来源：Room DB + CostEngine
- 点击：展开 App 主页
- Hilt 注入 `@AndroidEntryPoint`

---

## 开屏动画

多阶段 Canvas 绘制动画：

1. ⚡ 图标弹性缩放 Spring 入场
2. 「能耗手记」标题滑入
3. 波纹扩散 + 旋转电弧
4. 轨道粒子环绕运动
5. 点击任意位置直接跳过

---

## 新手引导

首次启动时显示，3 页 HorizontalPager：

| 页面 | 内容 |
|------|------|
| 1 | 📝 **智能输入** — 粘贴即解析，支持 17+ 种格式 |
| 2 | ⚡💧🔥 **三表合一** — 电表/水表/燃气表同时记录 |
| 3 | 📊 **分析与计费** — 阶梯电价 + 趋势图表 + AI 洞察 |

完成引导后 `DataStore` 标记 `onboarding_complete = true`，下次启动跳过。

---

## 每日主题分布

ThemeDist 每天自动切换渐变色主题：

- 基于日期计算 HSL 色相偏移，每天不同
- 折线图、KPI 卡片、进度条配色每日变化
- 可从设置页关闭（`themeDistEnabled`）
- 主题 JSON 缓存到 DataStore，避免每日请求

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

使用 **Open-Meteo** 免费天气 API，无需注册 Key，开箱即用。

### DeepSeek AI

在设置页填入 DeepSeek API Key（[platform.deepseek.com](https://platform.deepseek.com)），分析页即可使用 AI 分析。

---

## 测试

```
BUILD SUCCESSFUL — 14 test classes, all passed
```

| 类别 | 类名 | 覆盖 |
|------|------|------|
| 计费引擎 | `CostEngineTest` | 阶梯加价 / 三档触发 / 一档内 / 水费 |
| 输入解析 | `SmartInputParserTest` | 17+ 种解析模式 / 峰谷配对 / 极端输入 / 中文时间 |
| 自适应分类 | `AdaptiveClassifierTest` | 阈值学习 / 范围校验 / DataStore 缓存 |
| 异常检测 | `AnomalyDetectorTest` | 回退检测 / 突增校验 / 批量导入警告 |
| 月度预测 | `PredictiveAnalyzerTest` | 预测计算 / 快照保存 / 偏差对比 |
| 事件归因 | `EventImpactAnalyzerTest` | 标签时段对比 / 日均差异计算 |
| DeepSeek API | `DeepSeekRepositoryTest` | API 调用 / 空 Key 处理 / 解析降级 |
| 仓库层 | `MeterRepositoryTest` | 智能插入 / 批量导入 / 日期缺口插值 / 去重 |
| 天气插值 | `WeatherInterpolatorTest` | 线性插值 / 最近邻外推 / 已有数据跳过 |
| 天气 API | `WeatherRepositoryTest` | API 请求 / 响应解析 / 缓存命中 |
| 每日主题 | `ThemeDistRepositoryTest` | 主题获取 / JSON 缓存 / HSL 计算 |
| 用户偏好 | `UserPreferencesTest` | DataStore 读写 / 版本迁移 / 计费规则持久化 |
| 图表 | `ChartViewModelTest` | 图表数据聚合 / 时段切换 / 费用计算 |
| 小部件 | `EnergyFlowWidget` | 见 widget 目录 |

---

## 性能优化

### R8 混淆 + 资源压缩

```
Release: isMinifyEnabled = true + isShrinkResources = true
ProGuard: Room / Hilt / Ktor / kotlinx.serialization / Compose / ML Kit / Glance keep rules
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
| Glance 小部件数据缓存 | 避免频繁 Room 查询 |
| 5 分钟分类器再学习节流 | 避免频繁重算 |

---

## 依赖清单

| 类别 | 依赖 | 用途 |
|------|------|------|
| UI | Compose BOM + Material 3 + Icons Extended | 全 Compose 声明式 UI |
| 导航 | Navigation Compose | 三 Tab 导航（交叉淡入淡出动画） |
| 数据 | Room + KSP | 本地 SQLite 持久化 |
| 偏好 | DataStore Preferences | 设置/规则/阈值/缓存 |
| DI | Hilt + KSP | 依赖注入 |
| 网络 | Ktor Client (Android) | Open-Meteo + DeepSeek API |
| 序列化 | kotlinx.serialization | JSON 请求/响应解析 |
| 相机 | CameraX (core/camera2/lifecycle/view/extensions) | 拍照扫描电表/水表 |
| OCR | ML Kit Text Recognition Chinese | 中文电表数字识别 |
| 小部件 | Glance + Glance Material3 | 桌面 App Widget |
| 性能 | Profile Installer | ART 预编译优化 |
| 测试 | JUnit 4 | 14 个测试类的单元测试 |

---

## 许可证

MIT © 2026 Energy Flow Contributors
