# SharedExports, ReportExporter, ShareUtils

## SharedExports
`app/src/main/java/com/example/energyflow/data/SharedExports.kt`

### 用途
导出记录数据为可分享的文本格式，支持跨平台共享。

---

## ReportExporter
`app/src/main/java/com/example/energyflow/data/ReportExporter.kt`

### 用途
将 BillReportGenerator 生成的报告导出为文件，通过系统分享面板发送。

### 支持格式
- 纯文本 (.txt)
- HTML (.html)

---

## ShareUtils
`app/src/main/java/com/example/energyflow/data/ShareUtils.kt`

### 用途
Android 系统分享 Intent 封装。

---

## Converters — Room 类型转换器
`app/src/main/java/com/example/energyflow/data/Converters.kt`

### 转换
- `LocalDateTime` ↔ `Long` (epoch millis)
- Room 不直接支持 java.time.LocalDateTime，需要转换器

## DateTimeAdapters
`app/src/main/java/com/example/energyflow/data/DateTimeAdapters.kt`

### 用途
kotlinx.serialization 的 LocalDateTime 适配器
