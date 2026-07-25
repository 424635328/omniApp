# 外部服务集成

## WeatherRepository — Open-Meteo 天气 API
`app/src/main/java/com/example/energyflow/data/WeatherRepository.kt`

### API
- **免费，无需 API Key**
- 历史天气: `https://archive-api.open-meteo.com/v1/archive`
- 7天预报: `https://api.open-meteo.com/v1/forecast`
- 默认坐标: 南京 (32.06, 118.80)

### 数据模型
```kotlin
data class DailyWeather(
    val date: LocalDate,
    val tempMax: Double,      // 最高温度 °C
    val tempMin: Double,      // 最低温度 °C
    val textDay: String,      // 中文天气描述 (WMO码映射)
    val weatherCode: Int?,    // WMO天气码 (0-99)
    val precipitation: Double? // 降水量 mm
)
```

### WMO 天气码映射
0→晴, 1→少云, 2→多云, 3→阴, 45→雾, 61→小雨, 63→中雨, 71→小雪, 95→雷暴 等

### 使用场景
- `ChartViewModel` — 图表天气覆盖层 + 预测天气乘数
- `MainViewModel` — 最新记录日期的天气主题色
- `InsightGenerator` — 高温能耗飙升检测

---

## DeepSeekRepository — AI 分析
`app/src/main/java/com/example/energyflow/data/DeepSeekRepository.kt`

### API
- **需要 API Key** (用户在设置页配置)
- URL: `https://api.deepseek.com/v1/chat/completions`
- Model: `deepseek-chat`

### 两个用途
1. **全局分析** (`analyze()`) — SYSTEM_PROMPT: 150字以内条目式洞察
2. **自然语言解析** (`parseNaturalInput()`) — 降级解析，返回结构化文本

### 调用链
```
SmartInputParser 失败 → DeepSeekRepository.parseNaturalInput() → SmartInputParser.parseWithContext(force=true)
ChartViewModel.triggerAiAnalysis() → DeepSeekRepository.analyze(prompt)
```

---

## ThemeDistRepository — 每日主题分发
`app/src/main/java/com/example/energyflow/data/ThemeDistRepository.kt`

### API
- URL: `https://themedist.netlify.app/api/v1/today.json`
- 返回 CSS 变量 → 解析为 Compose 颜色

### 颜色映射
| CSS变量 | Compose颜色 |
|---------|------------|
| `--color-primary` | primary |
| `--color-secondary` | secondary |
| `--color-accent` | accent |
| `--color-bg` | background |
| `--color-surface` | surface |
| `--color-text` | text |
| `--color-text-muted` | textMuted |
| `--color-border` | border |

### 缓存策略
- 成功获取后缓存原始 JSON 到 DataStore
- 冷启动时从缓存恢复（避免闪烁）

---

## WeatherInterpolator — 天气数据插值
`app/src/main/java/com/example/energyflow/data/WeatherInterpolator.kt`

### 用途
Open-Meteo 返回的天气数据可能不覆盖所有消费记录日期，使用线性插值填补。

### 算法
- 目标日期有数据 → 直接使用
- 目标日期在已知点之间 → 线性插值 (lerp)
- 目标日期在所有已知点之前/之后 → 最近邻外推

### 调用方
`ChartScreen` 中 `remember(chartData, weatherData) { WeatherInterpolator.interpolate(...) }`
