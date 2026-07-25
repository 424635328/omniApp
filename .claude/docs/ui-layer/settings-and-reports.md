# Settings, Reports, Widget, Tile

## BillingSettingsScreen
`app/src/main/java/com/example/energyflow/ui/settings/BillingSettingsScreen.kt`
`app/src/main/java/com/example/energyflow/ui/settings/BillingSettingsViewModel.kt`

### 功能
- 计费规则编辑 (峰/谷/平电价, 阶梯阈值, 水价阶梯)
- API Key 配置 (DeepSeek, 天气)
- 天气城市 ID 配置
- 碳足迹因子配置
- 主题分发开关
- 月度账单报告生成 & 分享
- 数据管理 (导出/清空)

### 计费迁移
`UserPreferences.billingRules` 带版本号迁移:
- `CURRENT_BILLING_VERSION = 2`
- 版本低于当前 → 自动重置为南京默认值并写入 DataStore

---

## BillReportGenerator — 账单报告
`app/src/main/java/com/example/energyflow/data/BillReportGenerator.kt`

### 输出格式
1. **纯文本** (`generateTextReport()`) — 分隔线 + emoji + 对齐表格
2. **HTML** (`generateHtmlReport()`) — 深色主题 + 卡片布局 + CSS 变量

### 数据构建
- `buildReportData()` — 支持任意月份，计算峰谷/水费/燃气/标签统计
- `buildComparison()` — 环比数据 (与上月对比)

### 分享
通过 `ShareUtils` / `ReportExporter` 调用系统分享面板

---

## WrappedScreen — 年度报告
`app/src/main/java/com/example/energyflow/ui/WrappedScreen.kt`
`app/src/main/java/com/example/energyflow/ui/WrappedViewModel.kt`
`app/src/main/java/com/example/energyflow/ui/WrappedState.kt`

### 功能
- 年度/月度能耗总结
- 碳足迹 + 绿色徽章展示
- 动画报告卡片
- 图片导出

### 数据源
`shared/WrappedReport.kt` → `WrappedReportBuilder.build()`

---

## EnergyFlowWidget — 桌面小部件
`app/src/main/java/com/example/energyflow/widget/EnergyFlowWidget.kt`
`app/src/main/java/com/example/energyflow/widget/EnergyWidgetReceiver.kt`

### 功能
- Glance API 实现
- 显示最新电表读数 + 日均用电
- 快速记录入口

---

## EnergyTileService — 快速设置磁贴
`app/src/main/java/com/example/energyflow/ui/tile/EnergyTileService.kt`

### 功能
- Quick Settings Tile
- 点击打开 App

---

## QuickRecordActivity — 快速记录
`app/src/main/java/com/example/energyflow/ui/QuickRecordActivity.kt`

### 功能
- App Shortcut 入口
- Deep Link 支持
- 直接打开添加记录表单

---

## OcrSmartProcessor — OCR 处理
`app/src/main/java/com/example/energyflow/data/OcrSmartProcessor.kt`

### 功能
- CameraX 扫表识别
- 图片预处理 → OCR → SmartInputParser

## ImagePreprocessor — 图片预处理
`app/src/main/java/com/example/energyflow/data/ImagePreprocessor.kt`

### 功能
- 灰度化、二值化、降噪
- 提升 OCR 识别率
