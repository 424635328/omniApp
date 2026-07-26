# SharedExports, ReportExporter, ShareUtils

## SharedExports
`app/src/main/java/com/example/energyflow/data/SharedExports.kt`

### 用途
KMP 共享模块的 re-export 垫片（无导出逻辑）：
- `typealias BillingRules = SharedBillingRules` — 让既有 import 继续工作
- `typealias MonthPrediction = PredictiveAnalyzerShared.MonthPrediction`
- `@Serializable data class PredictionSnapshot(savedYearMonth, savedDayOfMonth, predictedTotalKwh, dailyRateKwh, consumedSoFarAtSave)` — 序列化到 DataStore，用于月度预测跟踪对比

---

## ReportExporter
`app/src/main/java/com/example/energyflow/data/ReportExporter.kt`

### 用途
用 Android Canvas + Paint 渲染 PNG 报告卡片（宽 1080px，高度按内容估算、最小 2400px），写入 MediaStore（`Pictures/EnergyFlow`），返回 Uri。不依赖 Compose。

### 特性
- 支持亮/暗主题（`ReportContent.isDarkTheme`）
- 卡片内容：KPI（用电/费用/CO₂）、水/燃气小计、峰谷平占比条、成就徽章、较上月对比、节能建议
- 仅输出 PNG，无 txt/html 格式

---

## ShareUtils
`app/src/main/java/com/example/energyflow/data/ShareUtils.kt`

### 用途
Android 系统分享 Intent 封装，共 5 个函数：
- `shareImage(context, uri)` — 分享 PNG 图片
- `shareText(context, text)` — 分享纯文本
- `shareHtml(context, html, title)` — 分享 HTML（text/html + EXTRA_HTML_TEXT）
- `shareImageToWeChat(context, uri)` — 定向分享到微信，未安装时回退通用分享面板
- `copyToClipboard(context, plainText, htmlText, label)` — 复制到剪贴板（可附带 HTML 样式）

---

## Converters — Room 类型转换器
`app/src/main/java/com/example/energyflow/data/Converters.kt`

### 转换
- `LocalDateTime` ↔ `String`（ISO_LOCAL_DATE_TIME 格式，非 epoch millis）
- Room 不直接支持 java.time.LocalDateTime，需要转换器

## DateTimeAdapters
`app/src/main/java/com/example/energyflow/data/DateTimeAdapters.kt`

### 用途
java.time ↔ kotlinx.datetime 的互转扩展函数（非 kotlinx.serialization 适配器）：
`toKtLocalDate` / `toKtLocalDateTime` / `toJavaLocalDate` / `toJavaLocalDateTime`
