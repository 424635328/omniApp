# 能耗手记 · Energy Flow

<p align="center">
  <strong>智能能耗追踪 · 峰谷电费计算 · 异常检测 · 天气关联 · 月度预测</strong><br/>
  <sub>Jetpack Compose · Material 3 · Hilt DI · Room · Ktor</sub>
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
- [异常检测](#异常检测)
- [事件归因分析](#事件归因分析)
- [天气关联](#天气关联)
- [月度预测](#月度预测)
- [UI 组件](#ui-组件)
- [构建与运行](#构建与运行)
- [测试](#测试)
- [性能优化](#性能优化)
- [依赖清单](#依赖清单)
- [许可证](#许可证)

---

## 项目简介

**能耗手记 (Energy Flow)** 为个人水电能耗记录而生的 Android App。

传统的能耗记录 App 要求逐项填写表单——但真实的记录场景往往是随手写下一行字：

```
7.14 17.17 16776
```

**Energy Flow 的一个核心信念：你的记录习惯不应该被表单束缚。** 粘贴什么，它就解析什么。

---

## 核心特性

### 🔌 智能输入与批量解析 (11 种正则模式)

| 模式 | 示例输入 | 解析结果 |
|------|---------|---------|
| 纯日期头 | `7.14` | 设置上下文日期 |
| 日期+时间+数值 | `7.13 01.23 16672 晚上读数` | 电表 16672 + 备注 |
| 紧凑时间+数值 | `7.13 0123 9310` | 01:23 峰电 9310 |
| 电表+水表同行 | `7.1 16639 880 两家` | 电 16639 + 水 880 |
| 中文时间+备注 | `6.26下午六点启用冰箱` | 18:00 事件记录 |
| 时间+纯备注 | `7.14 16.39 打开冰箱` | 纯事件标注 |
| 峰谷值配对 | `9310.75 / 7298.66` | 自动识别峰谷 |
| 水表前缀 | `水0879` | 水表 879 |
| 纯数值智能识别 | `16776` | 自动分类为电/水/峰/谷 |

### 📊 自适应数值分类器

```
用户数据 → 学习历史范围 → 动态阈值 → DataStore 缓存
   ↓
不再依赖硬编码的 15000/9000-10000/7000-8000/1000
   ↓
随着数据积累自动校准分类精度
```

### 💰 阶梯电价 + 峰谷电费计算

- 峰电/谷电/平电三段分时计价
- 三档阶梯电价 (200/400度分档)
- 水费独立计算
- 计费规则可自定义（DataStore 持久化）

### 🚨 预保存异常拦截

| 检测类型 | 触发条件 | 用户交互 |
|---------|---------|---------|
| 读数回退 | 新读数 < 上次记录 | 🔴 标红弹窗 → "确认保存" / "返回修改" |
| 耗量突增 | 单日耗电 ≥ 历史均值 500% | 🟠 橙色警告 → 详细数据对比 |

### 🔍 事件归因分析

```
备注含"冰箱"的记录 → 有标签时段日均 8.5 度/天
                  → 无标签时段日均 5.2 度/天
                  → 差异: +3.3 度/天 🔴
```

通过备注标签自动量化特定电器的能耗影响。

### 🌡 天气关联图层

- 接入和风天气 API (Ktor + kotlinx.serialization)
- 在折线图背景叠加温度曲线（最高温/最低温）
- 高温 (>32°C) 区域橙色标注
- 直观观察空调耗电与气温的关联

### 📈 月度预测

基于最近 5 条记录的日均耗电斜率，外推：

```
日均 12.3 度 × 剩余 18 天 + 已消耗 150 度 = 预计全月 371 度
预估电费 ¥ 223.00
```

### 📊 图表 kWh ↔ ¥ 一键切换

折线图 Y 轴单位、账单摘要全部跟随切换，直观对比耗量与花费。

---

## 技术架构

```
┌──────────────────────────────────────────┐
│              UI Layer (Compose)           │
│  MainScreen · ChartScreen · Settings     │
│  AnomalyWarningDialog · WeatherOverlay   │
└─────────────────┬────────────────────────┘
                  │ hiltViewModel()
┌─────────────────▼────────────────────────┐
│           ViewModel Layer                 │
│  MainViewModel · ChartViewModel          │
│  BillingSettingsViewModel                │
└─────────────────┬────────────────────────┘
                  │ @Inject
┌─────────────────▼────────────────────────┐
│           Domain / Data Layer             │
│  MeterRepository · CostEngine            │
│  SmartInputParser · AdaptiveClassifier   │
│  AnomalyDetector · PredictiveAnalyzer    │
│  EventImpactAnalyzer · WeatherRepository │
└──────┬──────────────────────┬────────────┘
       │                      │
┌──────▼────────┐    ┌───────▼──────────┐
│   Room DB     │    │  Preferences     │
│  (SQLite)     │    │  DataStore       │
└───────────────┘    └──────────────────┘
```

**依赖注入**: Hilt (SingletonComponent + ViewModelComponent)

**数据持久化**: Room (读/写记录) + Preferences DataStore (电价、主题、阈值缓存)

**天气 API**: Ktor HttpClient + kotlinx.serialization

---

## 项目结构

```
com.example.energyflow/
├── EnergyFlowApplication.kt          # @HiltAndroidApp
├── MainActivity.kt                   # @AndroidEntryPoint
├── di/
│   ├── DatabaseModule.kt             # Room DB + DAO 提供
│   ├── DataStoreModule.kt            # Preferences DataStore 提供
│   └── NetworkModule.kt              # Ktor HttpClient 提供
├── data/
│   ├── MeterRecord.kt                # Room Entity
│   ├── MeterRecordDao.kt             # Room DAO
│   ├── AppDatabase.kt                # Room Database
│   ├── Converters.kt                 # LocalDateTime ↔ String
│   ├── MeterRepository.kt            # 数据仓库 (智能插入/批量导入)
│   ├── SmartInputParser.kt           # 11 种正则解析模式
│   ├── ClassificationThresholds.kt   # 自适应阈值数据类
│   ├── AdaptiveClassifier.kt         # 从历史数据学习阈值
│   ├── AnomalyDetector.kt            # 异常检测 (单调/突增)
│   ├── CostEngine.kt                 # 阶梯电价+峰谷+水费计算
│   ├── PredictiveAnalyzer.kt         # 月度消耗/账单预测
│   ├── EventImpactAnalyzer.kt        # 标签事件能耗归因
│   ├── WeatherRepository.kt          # 和风天气 API
│   └── UserPreferences.kt            # DataStore 偏好管理
├── ui/
│   ├── MainScreen.kt                 # 时间轴主页 + 异常弹窗
│   ├── MainViewModel.kt              # 主页 ViewModel
│   ├── TimelineItem.kt               # 时间轴卡片
│   ├── chart/
│   │   ├── ChartScreen.kt            # 分析页 (账单/预测/事件/天气)
│   │   ├── ChartViewModel.kt         # 图表 ViewModel
│   │   ├── ConsumptionLineChart.kt   # 折线图 Canvas
│   │   └── WeatherOverlay.kt         # 温度曲线叠加层
│   ├── settings/
│   │   ├── BillingSettingsScreen.kt  # 计费规则设置
│   │   └── BillingSettingsViewModel.kt
│   ├── components/
│   │   ├── AddRecordSheet.kt         # 添加记录表单
│   │   ├── EditRecordSheet.kt        # 编辑记录表单
│   │   └── BatchImportSheet.kt       # 批量导入
│   ├── navigation/
│   │   └── AppNavGraph.kt            # 导航图 (3 Tab)
│   ├── theme/
│   │   ├── Color.kt                  # 霓虹色板
│   │   ├── Theme.kt                  # 暗黑主题
│   │   └── Type.kt                   # 等宽字体
│   └── utils/
│       └── Formatters.kt             # 格式化工具
└── test/
    └── SmartInputParserTest.kt       # 39 个测试用例
```

---

## 数据库设计

### MeterRecord (Room Entity)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, auto) | 主键 |
| timestamp | LocalDateTime | 记录时间 |
| isElectricRecorded | Boolean | 是否含电表 |
| electricTotal | Double? | 总电量 |
| electricPeak | Double? | 峰电量 |
| electricValley | Double? | 谷电量 |
| isWaterRecorded | Boolean | 是否含水表 |
| waterTotal | Double? | 水表读数 |
| note | String? | 备注/事件标签 |

---

## 智能解析器

SmartInputParser 采用**上下文关联解析**，支持 11 种正则模式：

1. `^\d{1,2}\.\d{1,2}$` — 纯日期头
2. `^\d{1,2}\.\d{1,2}\s+\d{1,2}\.\d{2}\s+\d+\.?\d*\s*.*$` — 日期+时间+数值
3. `^\d{1,2}\.\d{1,2}\s+\d{2}\d{2}\s+\d+\.?\d*\s*.*$` — 紧凑时间
4. `^\d{1,2}\.\d{1,2}\s+\d+\.?\d*\s+\d+\.?\d*\s*.*$` — 电+水
5. `^\d{1,2}\.\d{1,2}\s*(上午|下午)?\d{1,2}[点时]\d{0,2}分?\s*.*$` — 中文时间
6. `^\d{1,2}\.\d{1,2}\s+\d{1,2}\.\d{2}\s+\D.+$` — 时间+纯备注
7. `^\d{1,2}\.\d{2}\s+\d+\.?\d*\s*.*$` — 时间+数值(上下文)
8. `^\d{2}\d{2}\s+\d+\.?\d*\s*.*$` — 紧凑时间+数值(上下文)
9. `^\d{1,2}\.\d{2}\s*\D.+$` — 时间+备注(上下文)
10. `^水\s*\d+\.?\d*$` — 水表前缀
11. `^\d+\.?\d*$` — 纯数值(智能识别+峰谷配对)

支持中文数字转换（一→1, 十→10, 六→6 等）。

---

## 自适应分类器

AdaptiveClassifier 从用户历史数据中学习各仪表读数范围：

| 阈值 | 默认值 | 学习策略 |
|------|--------|---------|
| totalElectricMin | 15000 | avg(历史总电) × 0.85 |
| peakMin / peakMax | 9000 / 10000 | avg(历史峰电) × 0.85 / 1.15 |
| valleyMin / valleyMax | 7000 / 8000 | avg(历史谷电) × 0.85 / 1.15 |
| waterMax | 1000 | max(历史水表) × 1.2 |

新记录保存后自动 reLearn() → 缓存到 DataStore，避免冷启动重算。

---

## 计费引擎

### 阶梯电价

```
第一档 (0–200 度/月):  基准价 × 1.0
第二档 (201–400 度/月): 基准价 × 1.5
第三档 (>400 度/月):   基准价 × 2.0
```

### 分时电价

```
电费 = 阶梯(峰电, 峰价) + 阶梯(谷电, 谷价) + 阶梯(平电, 平价)
水费 = 水量 × 水价
```

所有单价通过 DataStore 持久化，可在设置页自定义。

---

## 异常检测

### 读数回退检测
保存前对比最新历史记录 → 新读数 < 历史 → 红色弹窗拦截

### 耗量突增检测
取最近两条记录的最新区间日均耗电 vs 更早记录的平均 → 比例 ≥ 5× → 橙色警告

---

## 事件归因分析

通过备注中的标签关键词（"冰箱"、"空调"、"洗衣机"等），对比**有标签时段**与**无标签时段**的日均耗电差异，量化特定电器的能耗贡献。

---

## 天气关联

- **API**: 和风天气 (QWeather) 免费版
- **数据**: 历史最高/最低温度
- **展示**: 温度曲线叠加在电量折线图背景
- **高温标记**: >32°C 区域橙色标注
- **配置**: 计费设置页填入 API Key 和城市 ID

---

## 月度预测

基于最近 5 条记录的日均消耗速率，线性外推：

```
日均速率 = (最新 - 最早) / 天数
预计全月 = 本月已耗 + 日均速率 × 剩余天数
预计账单 = CostEngine.calculateSimple(预计全月)
```

---

## UI 组件

| 组件 | 说明 |
|------|------|
| MainScreen | 时间轴列表 + FAB 菜单 + 添加/编辑/批量导入 Sheet |
| AnomalyWarningDialog | 异常拦截确认弹窗 |
| ChartScreen | 折线图 + 账单摘要 + 月度预测 + 事件归因 + 天气按钮 |
| ConsumptionLineChart | Canvas 折线图 (抗锯齿, Y轴标注) |
| WeatherOverlay | 温度曲线 Canvas 叠加层 |
| ToggleCostButton | kWh ↔ ¥ 一键切换 |
| BillingSettingsScreen | 峰/谷/平/水 单价 + API Key 设置 |
| TimelineItem | 时间轴卡片(峰谷值+备注+长按菜单) |
| AddRecordSheet | 添加表单(日期时间选择+峰谷展开+快捷标签) |
| BatchImportSheet | 批量粘贴导入 |

---

## 构建与运行

### 环境要求

- Android Studio 2024+
- JDK 17+
- Gradle 8.13
- Kotlin 2.1.20
- compileSdk 36, minSdk 34

### 构建命令

```bash
# 编译 Debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 安装到设备
./gradlew installDebug
```

### 和风天气 API Key

1. 访问 https://dev.qweather.com/ 注册免费账号
2. 在控制台创建应用，获取 API Key
3. 在 App 设置页填入 Key + 城市 LocationID
4. 图表页点击 🌡 按钮加载天气数据

---

## 测试

```
39 tests completed, 0 failed
```

### 覆盖范围

| 测试类别 | 用例数 | 覆盖内容 |
|---------|-------|---------|
| 正则模式验证 | 11+ | 全部 11 种输入格式 |
| 峰谷配对 | 2 | 批量导入配对逻辑 |
| 极端输入 | 12 | 乱码/空值/超长备注/零值/负值/emoji |
| 自适应阈值 | 2 | 动态阈值切换验证 |

### 极端输入覆盖

- 乱码 (`asdfghjkl!@#$`)
- 空输入 / 多空白行
- 500 字符超长备注
- 零值 / 超大值 (999999)
- 小数 (9310.75 / 7298.66)
- 非法日期 (2.31 → 自动修正)
- Tab 分隔符 / 负数值
- Emoji + 中文混合

---

## 性能优化

### Canvas 渲染
- 折线图和天气叠加层均使用精简绘制路径
- 避免 BlurMaskFilter，使用 alpha 叠加实现发光效果
- 数据点数量自适应 (节点≤6 个日期标签)

### 冷启动优化
- Baseline Profiles (androidx.profileinstaller)
- R8 dex-startup-optimization 实验性优化

### 依赖注入
- Hilt SingletonComponent 管理全局单例
- ViewModelComponent 自动管理 ViewModel 生命周期

---

## 依赖清单

| 类别 | 依赖 | 版本 |
|------|------|------|
| UI | Jetpack Compose BOM | 2024.09.00 |
| UI | Material 3 + Icons Extended | — |
| 导航 | Navigation Compose | 2.7.7 |
| 数据 | Room | 2.6.1 |
| 偏好 | DataStore Preferences | 1.0.0 |
| DI | Hilt | 2.51.1 |
| 网络 | Ktor Client (Android) | 2.3.7 |
| 序列化 | kotlinx.serialization | 1.6.2 |
| 性能 | Profile Installer | 1.3.1 |
| 测试 | JUnit 4 | 4.13.2 |

---

## 许可证

MIT © 2025 Energy Flow Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
