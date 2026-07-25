# SmartInputParser — 智能输入解析

## File
`app/src/main/java/com/example/energyflow/data/SmartInputParser.kt`

## 设计目标
支持多种自然语言格式的能耗数据输入，自动识别电表/水表/燃气/峰谷，无需用户手动选择类型。

## 解析模式（按优先级）

| 模式 | 输入格式 | 示例 |
|------|---------|------|
| 1 | 纯日期头 | `7.15` |
| 2 | 日期+时间+数值 | `7.15 14.30 16639` |
| 3 | 日期+紧凑时间+数值 | `7.15 1430 16639` |
| 4 | 日期+电表+水表 | `7.15 16639 880` (大值=电，小值=水) |
| 5 | 日期+中文时间+备注 | `7.15 下午三点十五分 开冰箱` |
| 5b | 日期+时间(无数值) | `7.15 14.30` |
| 6 | 日期+时间+备注 | `7.15 14.30 开冰箱` |
| 6b | 时间+标记值 | `14.30 电16639 水880 气12.34 峰678 谷901 备注` |
| 7a | 时间+电表+水表 | `12.00 16639 880 两家` |
| 7 | 时间+数值 | `14.30 16639` |
| 8 | 紧凑时间+数值 | `1430 16639` |
| 9 | 时间+备注 | `14.30 开冰箱` |
| 9a | 时间+水表标记 | `14.30 水880` |
| 9b | 时间+燃气标记 | `14.30 气12.34` |
| 10 | 水表前缀 | `水880` |
| 10b | 燃气前缀 | `气12.34` |
| 11 | 纯数值(智能分类) | `16639` (自动判断电/水/峰/谷) |

## 智能分类逻辑 (classifyValue)
使用 `ClassificationThresholds` 动态阈值：
```
< waterMax            → 水表
[peakMin, peakMax]    → 峰电
[valleyMin, valleyMax] → 谷电
>= totalElectricMin   → 总电表
其他                   → 默认总电表
```

## 上下文关联 (parseWithContext)
- 日期头 `7.15` 设置 currentMonth/currentDay，后续行继承
- 峰谷配对：`PendingElectric` 状态机，先收到峰再收到谷时自动合并
- 备注中提取 `峰X` `谷X` 标记

## AI 降级路径
```
SmartInputParser.parseWithContext() → 失败
    ↓
DeepSeekRepository.parseNaturalInput() → 返回结构化文本
    ↓
SmartInputParser.parseWithContext(force=true) → 二次解析
```

## ClassificationThresholds
`data/ClassificationThresholds.kt` — 默认值 + AdaptiveClassifier 自适应学习

| 字段 | 默认值 | 含义 |
|------|--------|------|
| totalElectricMin | 15000 | 总电表最小值 |
| peakMin/peakMax | 9000/10000 | 峰电区间 |
| valleyMin/valleyMax | 7000/8000 | 谷电区间 |
| waterMax | 1000 | 水表上限 |
