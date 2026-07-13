# 能耗手记 · Energy Flow

<p align="center">
  <strong>一款为个人能耗记录习惯深度定制的 Android App</strong><br/>
  <sub>智能解析 · 时间轴可视化 · 霓虹暗黑 · Material 3</sub>
</p>

---

## 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
  - [智能输入系统](#1-智能输入系统)
  - [批量导入引擎](#2-批量导入引擎)
  - [数据可视化](#3-数据可视化)
  - [交互体验](#4-交互体验)
  - [设计语言](#5-设计语言)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [数据库设计](#数据库设计)
- [智能解析器](#智能解析器)
- [UI 组件](#ui-组件)
- [构建与运行](#构建与运行)
- [使用指南](#使用指南)
- [代码质量](#代码质量)
- [依赖清单](#依赖清单)
- [许可证](#许可证)

---

## 项目简介

**能耗手记 (Energy Flow)** 是一款为个人能耗数据记录而生的 Android App。它的设计出发点是：**你的记录习惯不应该被 App 的表单束缚**。

传统的能耗记录 App 要求你逐项填写日期、时间、数值——但真实的记录场景往往是随手记下一行字：

```
7.14 17.17 16776
```

能耗手记的核心理念是：**让 App 适应你，而不是你适应 App**。你只需要把原始数据粘贴进去，剩下的交给智能解析器。

### 设计灵感

| 来源 | 提取 |
|------|------|
| 个人记录习惯 | 时间不规律、包含备注、多维度数据（峰/谷）、水电组合记录 |
| 微信聊天框 | 底部悬浮输入框，一键发送 |
| 时间轴社交应用 | 每条记录是时间线上的一个节点 |
| 极客终端 | 等宽字体、霓虹色彩、暗黑底色 |

---

## 核心特性

### 1. 智能输入系统

两种输入模式，覆盖所有场景：

#### 表单模式（精确录入）

点击右下角 **+** 按钮，滑出底部表单：

| 组件 | 功能 |
|------|------|
| 📅 日期选择器 | Material 3 DatePicker，可视化选择 |
| 🕐 时间选择器 | Material 3 TimePicker，滚轮式选择 |
| ⚡ 电表输入 | 总电量 + 可展开的峰/谷明细 |
| 💧 水表输入 | 独立开关，按需启用 |
| ✅ 峰谷校验 | 自动计算 `峰 + 谷` 与总表的差值，误差 < 0.1 显示绿色勾 |
| 🏷️ 快捷标签 | ❄️开冰箱 · 🔇关冰箱 · 👥两家合用 · ❄️空调 · 🧺洗衣机 |

#### 文本模式（极速录入）

点击右下角 **粘贴** 按钮，粘贴原始数据，一键导入。

---

### 2. 批量导入引擎

智能解析器支持 **11 种数据格式**，覆盖你所有的记录习惯：

| # | 格式 | 示例 | 解析结果 |
|---|------|------|----------|
| 1 | 纯日期头 | `7.14` | 设置日期上下文 |
| 2 | 日期 + 时间 + 数值 | `7.13 01.23 16672` | 7月13日 01:23 电表 16672 |
| 3 | 日期 + 紧凑时间 + 数值 | `7.12 1228 16765` | 7月12日 12:28 电表 16765 |
| 4 | 日期 + 电表 + 水表 + 备注 | `7.1 16639 880 两家` | 7月1日 电表 16639 水表 880 备注"两家" |
| 5 | 日期 + 中文时间 + 备注 | `6.26下午六点开始启用冰箱` | 6月26日 18:00 备注"开始启用冰箱" |
| 6 | 日期 + 时间 + 备注 | `6.29 17.06 太吵了停止使用冰箱` | 6月29日 17:06 备注"太吵了停止使用冰箱" |
| 7 | 时间 + 数值（继承日期） | `17.17 16776` | 继承当前日期上下文 |
| 8 | 紧凑时间 + 数值 | `1228 16765` | 继承当前日期上下文 |
| 9 | 时间 + 备注 | `16.39 打开冰箱` | 继承当前日期上下文 |
| 10 | 水表前缀 | `水0879` | 水表 879 |
| 11 | 纯数值 | `16626` / `9310.75` | 自动判断类型（电表/水表/峰谷值） |

#### 上下文继承机制

```
7.14                    ← 设置日期上下文：7月14日
17.17 16776             ← 继承 7.14，解析为 7.14 17:17 电表 16776
16.39 打开冰箱           ← 继承 7.14，解析为 7.14 16:39 备注"打开冰箱"
15.09 16774             ← 继承 7.14，解析为 7.14 15:09 电表 16774
7.13 01.23 16672        ← 更新日期上下文：7月13日
17.58 16767             ← 继承 7.13，解析为 7.13 17:58 电表 16767
```

#### 智能类型判断

| 数值范围 | 判断类型 | 示例 |
|----------|----------|------|
| ≥ 10000 | 电表 | `16776` → 电表 16776 度 |
| 4000 ~ 10000 | 峰/谷值 | `9310.75` → 峰电，`7298.66` → 谷电 |
| < 1000 | 水表 | `880` → 水表 880 吨 |

#### 中文数字支持

自动将中文数字转换为阿拉伯数字：

| 输入 | 转换后 |
|------|--------|
| `下午六点` | `下午6点` |
| `三点十五分` | `3点15分` |
| `上午八点半` | `上午8点30分` |

---

### 3. 数据可视化

#### 时间轴主页

- **倒序排列** — 最新记录在最上方
- **卡片式布局** — 电表/水表分别显示在独立卡片中
- **峰谷明细** — 直接在卡片上显示峰/谷值
- **备注高亮** — 备注以蓝色渐变标签显示
- **长按删除** — 长按卡片弹出确认对话框
- **滑入动画** — 列表项使用 spring 物理弹簧动画

#### 能耗分析页

- **自定义折线图** — 使用 Canvas 绘制，支持霓虹发光效果
- **时间范围选择** — 周 / 月 / 年 / 全部
- **数据摘要** — 记录数量、最新读数、日均用电、总用电量
- **事件标注** — 备注记录在图表上显示为垂直标注线

---

### 4. 交互体验

| 交互 | 实现 |
|------|------|
| FAB 缩放动画 | `animateFloatAsState` + `spring` 物理弹簧 |
| 卡片按下反馈 | 0.98x 缩放 + 阴影变化 |
| 底部表单滑入 | `slideInVertically` + `spring` |
| 列表项动画 | `animateItem` + `fadeIn` + `placementSpec` |
| 峰谷展开/收起 | `expandVertically` / `shrinkVertically` |
| 按钮点击 | `scale` 动画 + 阴影增强 |
| 删除确认 | Material 3 `AlertDialog` |
| Snackbar 提示 | 自定义颜色和圆角 |

---

### 5. 设计语言

#### 色彩系统

| 用途 | 颜色 | 色值 |
|------|------|------|
| 电表 | 霓虹黄 | `#FFFF00` |
| 峰电 | 霓虹橙 | `#FF6600` |
| 谷电 | 霓虹青 | `#00FFFF` |
| 水表 | 流体蓝 | `#00BFFF` |
| 备注 | 霓虹蓝 | `#00BFFF` |
| 背景 | 深空黑 | `#0A0A0A` |
| 卡片 | 暗灰 | `#2A2A2A` |
| 成功 | 霓虹绿 | `#00FF88` |
| 错误 | 柔红 | `#FF6B6B` |

#### 字体

- **全局等宽字体** — `FontFamily.Monospace`
- **数据数字** — 22sp Bold，突出科技感
- **标签文字** — 10sp，低调但清晰

#### 圆角规范

| 元素 | 圆角 |
|------|------|
| 卡片 | 16dp |
| 输入框 | 10dp |
| 按钮 | 12dp |
| 标签 | 20dp |
| 底部表单 | 24dp (顶部) |

---

## 技术架构

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │MainScreen│ │ChartScreen│ │  Components      │ │
│  │Timeline  │ │LineChart │ │ AddRecordSheet   │ │
│  │TopBar    │ │Summary   │ │ BatchImportSheet │ │
│  │EmptyState│ │Annotation│ │                  │ │
│  └────┬─────┘ └────┬─────┘ └────────┬─────────┘ │
│       │             │                │           │
│  ┌────▼─────────────▼────────────────▼─────────┐ │
│  │              ViewModels                      │ │
│  │  MainViewModel · ChartViewModel              │ │
│  └──────────────────┬──────────────────────────┘ │
├─────────────────────┼───────────────────────────┤
│                Data Layer                        │
│  ┌──────────────────▼──────────────────────────┐ │
│  │           MeterRepository                    │ │
│  │  ┌──────────────┐  ┌─────────────────────┐  │ │
│  │  │SmartInputParser│  │   MeterRecordDao   │  │ │
│  │  │ (11 patterns) │  │  (Room · SQLite)    │  │ │
│  │  └──────────────┘  └─────────────────────┘  │ │
│  └─────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────┤
│               Theme Layer                        │
│  Color.kt · Type.kt · Theme.kt · Formatters.kt  │
└─────────────────────────────────────────────────┘
```

### 架构模式

**MVVM (Model-View-ViewModel)**

| 层 | 职责 | 关键类 |
|----|------|--------|
| **View** | UI 渲染、用户交互 | `MainScreen`, `ChartScreen`, `AddRecordSheet` |
| **ViewModel** | 状态管理、业务逻辑 | `MainViewModel`, `ChartViewModel` |
| **Repository** | 数据访问抽象 | `MeterRepository` |
| **Data** | 数据库、解析器 | `AppDatabase`, `SmartInputParser` |

### 数据流

```
用户输入 → SmartInputParser.parseWithContext()
         → List<ParseResult>
         → MeterRepository.batchInsert()
         → MeterRecordDao.insert()
         → Room (SQLite)
         → Flow<List<MeterRecord>>
         → MainViewModel.allRecords
         → MainScreen (Compose recomposition)
```

---

## 项目结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/energyflow/
│   ├── MainActivity.kt                    # 入口 Activity
│   │
│   ├── data/                              # 数据层
│   │   ├── AppDatabase.kt                 # Room 数据库定义
│   │   ├── Converters.kt                  # LocalDateTime 类型转换器
│   │   ├── MeterRecord.kt                 # 实体类（电表/水表/燃气表）
│   │   ├── MeterRecordDao.kt              # DAO 接口（CRUD + 查询）
│   │   ├── MeterRepository.kt             # 数据仓库（业务逻辑封装）
│   │   └── SmartInputParser.kt            # 智能解析器（11 种格式 + 上下文继承）
│   │
│   └── ui/                                # 表现层
│       ├── MainScreen.kt                  # 主屏幕（时间轴 + FAB + 底部表单）
│       ├── MainViewModel.kt               # 主视图模型（状态管理）
│       ├── TimelineItem.kt                # 时间轴卡片组件（长按删除）
│       │
│       ├── chart/                         # 图表模块
│       │   ├── ChartScreen.kt             # 分析页面（折线图 + 数据摘要 + 标注）
│       │   ├── ChartViewModel.kt          # 图表视图模型
│       │   └── ConsumptionLineChart.kt    # 自定义 Canvas 折线图（霓虹发光）
│       │
│       ├── components/                    # 通用组件
│       │   ├── AddRecordSheet.kt          # 添加记录底部表单（日期/时间/数值/备注）
│       │   └── BatchImportSheet.kt        # 批量导入底部表单（粘贴 + 解析）
│       │
│       ├── navigation/                    # 导航
│       │   └── AppNavGraph.kt             # 底部导航栏（记录 / 分析）
│       │
│       ├── theme/                         # 主题
│       │   ├── Color.kt                   # 霓虹色彩常量
│       │   ├── Theme.kt                   # Material 3 暗黑主题
│       │   └── Type.kt                    # 等宽字体排版系统
│       │
│       └── utils/                         # 工具
│           └── Formatters.kt              # 数值格式化（locale-safe）
│
└── res/
    ├── values/
    │   ├── strings.xml                    # 字符串资源
    │   └── styles.xml                     # 主题样式
    └── ...
```

### 文件统计

| 指标 | 数值 |
|------|------|
| Kotlin 文件 | 20 |
| 代码总行数 | ~3900 |
| 数据层文件 | 6 |
| UI 层文件 | 11 |
| 主题/工具文件 | 4 |

---

## 数据库设计

### 实体：MeterRecord

```sql
CREATE TABLE meter_records (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp           DATETIME NOT NULL,          -- 绝对时间，如 2026-07-14 17:17:00
    is_electric_recorded BOOLEAN DEFAULT FALSE,     -- 是否记录了电表
    electric_total      REAL,                       -- 总电表读数，如 16776
    electric_peak       REAL,                       -- 峰电读数，如 9310.75
    electric_valley     REAL,                       -- 谷电读数，如 7298.66
    is_water_recorded   BOOLEAN DEFAULT FALSE,      -- 是否记录了水表
    water_total         REAL,                       -- 水表读数，如 880
    is_gas_recorded     BOOLEAN DEFAULT FALSE,      -- 预留：燃气表
    gas_total           REAL,                       -- 预留：燃气读数
    note                TEXT                        -- 备注，如 "打开冰箱"
);
```

### DAO 接口

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `insert(record)` | `Long` | 插入记录，返回 ID |
| `update(record)` | `Unit` | 更新记录 |
| `delete(record)` | `Unit` | 删除记录 |
| `getAllRecords()` | `Flow<List<MeterRecord>>` | 全部记录（倒序） |
| `getRecordsByTimeRange(start, end)` | `Flow<List<MeterRecord>>` | 时间范围查询 |
| `getLatestRecord()` | `MeterRecord?` | 最新一条记录 |
| `getPreviousRecord(time)` | `MeterRecord?` | 指定时间之前的记录 |
| `getElectricRecords()` | `Flow<List<MeterRecord>>` | 仅电表记录 |
| `getWaterRecords()` | `Flow<List<MeterRecord>>` | 仅水表记录 |
| `getRecordsWithNotes()` | `Flow<List<MeterRecord>>` | 有备注的记录 |
| `getRecordCount()` | `Flow<Int>` | 记录总数 |
| `deleteAll()` | `Unit` | 清空所有记录 |

### 类型转换

Room 不直接支持 `LocalDateTime`，通过 `Converters` 类实现：

```kotlin
class Converters {
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): String? {
        return date?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}
```

---

## 智能解析器

### 解析流程

```
输入文本
  │
  ├─ 预处理：中文数字转换（六 → 6）
  │
  ├─ 逐行解析（带上下文）
  │   ├─ 匹配模式 1：纯日期头（7.14）
  │   ├─ 匹配模式 2：日期 + 时间 + 数值（7.13 01.23 16672）
  │   ├─ 匹配模式 3：日期 + 紧凑时间 + 数值（7.12 1228 16765）
  │   ├─ 匹配模式 4：日期 + 电表 + 水表（7.1 16639 880 两家）
  │   ├─ 匹配模式 5：日期 + 中文时间 + 备注（6.26下午六点...）
  │   ├─ 匹配模式 5b：日期 + 时间 无数值（6.28 22.29）
  │   ├─ 匹配模式 6：日期 + 时间 + 备注（6.29 17.06 太吵了...）
  │   ├─ 匹配模式 7：时间 + 数值（17.17 16776）← 继承日期
  │   ├─ 匹配模式 8：紧凑时间 + 数值（1228 16765）← 继承日期
  │   ├─ 匹配模式 9：时间 + 备注（16.39 打开冰箱）← 继承日期
  │   ├─ 匹配模式 10：水表前缀（水0879）
  │   └─ 匹配模式 11：纯数值（16626 / 9310.75）
  │
  ├─ 峰谷值合并
  │   └─ 9310.75 + 7298.66 = 16609.41 ≈ 16609.42 ✅
  │
  └─ 输出：List<ParseResult.Success | ParseResult.Error>
```

### 正则表达式一览

```kotlin
// 模式 1：纯日期头
Regex("""^(\d{1,2})\.(\d{1,2})$""")

// 模式 2：日期 + 时间 + 数值
Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})\s+(\d+\.?\d*)\s*(.*)$""")

// 模式 3：日期 + 紧凑时间 + 数值
Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{2})(\d{2})\s+(\d+\.?\d*)\s*(.*)$""")

// 模式 4：日期 + 电表 + 水表
Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d+\.?\d*)\s+(\d+\.?\d*)\s*(.*)$""")

// 模式 5：日期 + 中文时间
Regex("""^(\d{1,2})\.(\d{1,2})\s*(上午|下午)?(\d{1,2})[点时](\d{0,2})分?\s*(.*)$""")

// 模式 5b：日期 + 时间 无数值
Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})$""")

// 模式 6：日期 + 时间 + 备注
Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2})\.(\d{2})\s+(\D.+)$""")

// 模式 7：时间 + 数值
Regex("""^(\d{1,2})\.(\d{2})\s+(\d+\.?\d*)\s*(.*)$""")

// 模式 8：紧凑时间 + 数值
Regex("""^(\d{2})(\d{2})\s+(\d+\.?\d*)\s*(.*)$""")

// 模式 9：时间 + 备注
Regex("""^(\d{1,2})\.(\d{2})\s*(\D.+)$""")

// 模式 10：水表前缀
Regex("""^水\s*(\d+\.?\d*)$""")

// 模式 11：纯数值
Regex("""^(\d+\.?\d*)$""")
```

---

## UI 组件

### MainScreen

主屏幕，包含：
- **TopBar** — 渐变背景标题栏，显示"能耗手记"和记录总数
- **LazyColumn** — 时间轴列表，支持 `animateItem` 动画
- **EmptyState** — 空状态引导，脉冲动画图标
- **双 FAB** — 添加记录（大）+ 批量导入（小），带缩放动画
- **底部表单** — `AnimatedVisibility` 滑入滑出

### TimelineItem

时间轴卡片，包含：
- 左侧日期/时间指示器（带发光圆点）
- 电表卡片（峰/谷明细）
- 水表卡片
- 备注标签（渐变背景）
- 长按删除（`AlertDialog` 确认）
- 按下缩放动画（0.98x）

### AddRecordSheet

添加记录底部表单，包含：
- 日期/时间选择（`DateTimeChip`）
- 电表输入（可展开峰谷）
- 水表输入（独立开关）
- 峰谷校验提示
- 备注快捷标签
- 保存按钮（启用/禁用动画）

### BatchImportSheet

批量导入底部表单，包含：
- 格式说明卡片
- 多行文本输入框
- 导入按钮

### ConsumptionLineChart

自定义 Canvas 折线图，包含：
- 网格线 + Y 轴标签
- 霓虹发光折线（双层绘制：外层模糊 + 内层实线）
- 数据点（发光圆点）
- X 轴日期标签

### ChartScreen

分析页面，包含：
- 时间范围选择按钮（周/月/年/全部）
- 折线图
- 数据摘要卡片（记录数、最新读数、日均用电、总用电量）
- 事件标注列表

---

## 构建与运行

### 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| JDK | 11 |
| Kotlin | 2.1.20 |
| AGP | 8.13.0 |
| Gradle | 8.13 |

### 快速开始

```bash
# 1. 克隆仓库
git clone <repo-url>
cd omniAPP

# 2. 构建 Debug APK
./gradlew assembleDebug

# 3. 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk

# 4. 或在 Android Studio 中直接运行
# File → Open → 选择项目目录 → Run ▶
```

### 常用命令

```bash
# 编译检查
./gradlew :app:compileDebugKotlin

# Lint 检查
./gradlew :app:lint

# 构建 Release APK
./gradlew assembleRelease

# 清理构建
./gradlew clean
```

---

## 使用指南

### 首次使用

1. 打开 App，看到空状态引导页
2. 点击右下角 **+** 按钮，手动添加第一条记录
3. 或点击 **粘贴** 按钮，粘贴历史数据批量导入

### 手动添加记录

```
1. 点击 + 按钮
2. 选择日期和时间
3. 输入电表读数（可展开输入峰/谷值）
4. 如需记录水表，打开水表开关
5. 添加备注（可选快捷标签）
6. 点击"保存记录"
```

### 批量导入数据

```
1. 点击粘贴按钮
2. 在文本框中粘贴原始数据
3. 点击"开始导入"
4. 查看导入结果
```

**示例数据：**

```
7.14
17.17 16776
16.39 打开冰箱
15.09 16774
11.39 16773
7.13 01.23 16672
17.58 16767
7.12 1228 16765
7.11 23.25 16763.39
7.11 18.55 16760
7.8 10.24 16707
7.4 17.14 16668
7.2 18.34 16653
7.2 09.54 16647
7.1 16639 880 两家
6.29 17.06 太吵了停止使用冰箱
16626
6.28 22.29
16621
6.26 18.00 开始启用冰箱
0879
16609.42
9310.75
7298.66
6.23
16602.55
9308.67
7293.88
水0879
```

### 删除记录

- 在时间轴上 **长按** 任意卡片
- 在弹出的确认对话框中点击"删除"

### 查看分析

- 切换到 **分析** 标签页
- 选择时间范围（周/月/年/全部）
- 查看折线图和数据摘要

---

## 代码质量

| 检查项 | 状态 |
|--------|------|
| 编译 | ✅ BUILD SUCCESSFUL |
| Lint | ✅ 通过（97 个依赖版本提示警告） |
| APK | ✅ 生成成功（~25MB） |
| 架构 | ✅ MVVM 分层清晰 |
| 类型安全 | ✅ Room + Kotlin 类型系统 |
| 格式化 | ✅ Locale-safe Formatters |

---

## 依赖清单

### 核心依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `androidx.core:core-ktx` | 1.10.1 | Kotlin 扩展 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.1 | 生命周期感知 |
| `androidx.activity:activity-compose` | 1.8.0 | Compose Activity |
| `androidx.compose:compose-bom` | 2024.09.00 | Compose BOM |
| `androidx.compose.material3:material3` | - | Material 3 UI |
| `androidx.compose.material:material-icons-extended` | - | 扩展图标库 |
| `androidx.navigation:navigation-compose` | 2.7.7 | Compose 导航 |
| `androidx.room:room-runtime` | 2.6.1 | Room 数据库 |
| `androidx.room:room-ktx` | 2.6.1 | Room Kotlin 扩展 |
| `androidx.room:room-compiler` | 2.6.1 | Room 注解处理器 (KSP) |

### 构建插件

| 插件 | 版本 |
|------|------|
| `com.android.application` | 8.13.0 |
| `org.jetbrains.kotlin.android` | 2.1.20 |
| `org.jetbrains.kotlin.plugin.compose` | 2.1.20 |
| `com.google.devtools.ksp` | 2.1.20-1.0.31 |

---

## 许可证

```
MIT License

Copyright (c) 2026

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
```

---

<p align="center">
  <sub>Built with Kotlin + Jetpack Compose · 2026</sub><br/>
  <sub>能耗手记 · Energy Flow</sub>
</p>
# omniApp
