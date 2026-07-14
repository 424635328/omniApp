# 能耗手记 / EnergyFlow — 单元测试报告

> 生成时间: 2026-07-14
> 运行命令: `./gradlew :app:testDebugUnitTest`

---

## 总览

| 指标 | 数值 |
|------|------|
| 测试类数 | **14** |
| 总测试用例 | **173** |
| 通过 | **173** ✅ |
| 失败 | **0** |
| 跳过 | **0** |
| 新增/扩增 | **7 个新测试类 + 136 个新用例** |

---

## 各测试类详情

### 1. `WeatherInterpolatorTest` — 天气数据插值器 🆕

**测试数: 17 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **空输入** | 3 | 空天气数据、空目标日期、两者皆空 |
| **精确匹配** | 1 | 所有目标日期都有精确天气数据 |
| **线性插值** | 2 | 两点间插值、多点间插值 |
| **外推** | 2 | 目标在已知点之前、之后 |
| **单点** | 1 | 仅一个已知数据点覆盖所有目标 |
| **混合场景** | 1 | 同时包含精确+插值+外推 |
| **无效过滤** | 2 | 无效温度被过滤、全无效返回空 |
| **降水插值** | 2 | 降水线性插值、两端的 null 降水传递 |
| **天气码外推** | 1 | textDay/weatherCode 最近邻赋值 |
| **大跨度** | 1 | 跨半年的稀疏已知点插值 |
| **非连续日期** | 1 | 目标日期不是连续的 |

---

### 2. `WeatherRepositoryTest` — 天气 API 响应解析 🆕

**测试数: 20 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **成功解析** | 2 | 单日全字段、多日所有字段 |
| **WMO 码 → 中文** | 5 | 0→晴、61→小雨、95→雷暴、未知码→空、null→空 |
| **API 错误** | 2 | error=true + reason、error=true 无 reason |
| **缺失字段** | 2 | 无 daily 区块、空 time 数组 |
| **字段不完整** | 1 | 无 temperature_2m_max 导致全被跳过 |
| **数组长度不匹配** | 1 | 字段长度不一致返回错误 |
| **空值处理** | 1 | 某日 tempMax 为 null 则跳过该日 |
| **无效日期** | 1 | 无效日期字符串被跳过，有效日期保留 |
| **可选字段缺失** | 2 | 无 precipitation、无 weathercode 仍能解析 |
| **边界值** | 1 | 极端温度（45°C / -15°C）被正常接收 |
| **混合场景** | 1 | 多日中部分字段 null 的正确处理 |
| **全无效** | 1 | 全部 null 温度返回错误 |

---

### 3. `PredictiveAnalyzerTest` — 月耗预测 🆕

**测试数: 16 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **记录不足** | 4 | 空列表、无电记录、单条记录、不足2条 |
| **递减过滤** | 2 | 递减记录被过滤、全递减返回 null |
| **本月推算** | 4 | 正常预测、首尾使用、精确剩余天数、零增长返回 null |
| **历史回退** | 2 | 本月1条用最近记录、0条回退上月 |
| **月份边界** | 2 | 跨月记录、月初/月末预测 |
| **边界值** | 2 | 恰好2条、大跨度记录 |

---

### 4. `EventImpactAnalyzerTest` — 事件影响分析 📈

**测试数: 9（原1→扩至9）| 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **标准流程** | 1 | 冰箱启停事件正确计算 delta |
| **空结果** | 2 | 无标签、空备注 |
| **未配对** | 2 | 有开始无结束（窗口延伸至末尾）、有结束无开始（忽略） |
| **标签风格** | 2 | `#HashTag` 风格识别、已知电器名无 `#` 匹配 |
| **多标签** | 1 | 同时处理冰箱和空调两个独立事件 |
| **记录不足** | 1 | <3 条电记录返回空 |

---

### 5. `AdaptiveClassifierTest` — 自适应阈值分类器 🆕

**测试数: 8 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **空记录** | 2 | 无缓存回退默认值、有缓存返回缓存 |
| **纯电** | 1 | 电表记录计算 totalElectricMin |
| **峰谷** | 1 | 含峰谷值的电记录计算正确区间 |
| **纯水** | 1 | 水表记录计算 waterMax |
| **混合** | 1 | 电+水记录同时算正确阈值 |
| **重学** | 1 | reLearn 触发重新计算并缓存 |
| **下限** | 1 | totalElectricMin 不低于 5000 |

---

### 6. `ThemeDistRepositoryTest` — 每日主题 🆕

**测试数: 10 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **parseColors 6位 hex** | 1 | `#FF6600` → R=1.0, G=0.4, B=0.0 |
| **parseColors 3位 hex** | 1 | `#F60` → 展开为 `#FF6600` |
| **parseColors rgba** | 1 | `rgba(255,102,0,0.8)` → alpha=0.8 |
| **parseColors rgb** | 1 | `rgb(255,102,0)` → alpha=1.0 |
| **parseColors 无效值** | 1 | 非法格式回退默认值 |
| **parseColors 空 map** | 2 | 空 map、emptyMap 都回退默认值 |
| **loadCachedResponse** | 3 | 无缓存返回 null、解析缓存 JSON、无效 JSON 返回 null |

---

### 7. `DeepSeekRepositoryTest` — AI 分析客户端 🆕

**测试数: 8 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **无 Key** | 2 | 空 Key、空白 Key 返回 null |
| **成功响应** | 2 | 正常内容返回、多 choice 取第一个 |
| **空响应** | 2 | 空 choices 返回 null、message content 为 null 返回 null |
| **HTTP 错误** | 1 | 401 Unauthorized 静默返回 null |
| **异常响应** | 1 | 非法 JSON 静默返回 null |

---

### 8. `UserPreferencesTest` — 用户偏好 🆕

**测试数: 14 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **默认值** | 5 | 主题、图表、设备、deepseek Key、天气 Key |
| **计费规则** | 3 | 版本0用南京默认、版本2用保存值、旧版迁移重置 |
| **阈值缓存** | 2 | 未设置返回 null、正确解析 |
| **天气预报缓存** | 1 | 存储和读取 |
| **写操作** | 3 | 设置主题、缓存阈值、缓存预报不抛异常 |

---

### 9. `MeterRepositoryTest` — 仪表仓库 🆕

**测试数: 14 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **smartInsert** | 5 | 有效→Success、不可解析→Error、单调异常→Warning、突增→Warning、force跳过 |
| **batchInsert** | 2 | 有效→Success、部分错误→PartialSuccess |
| **calculateConsumption** | 3 | 有历史→Success、无历史→NoPrevious、纯电 |
| **CRUD 委托** | 4 | insert/update/delete/getAllRecords 委托给 DAO |

---

### 10. `ChartViewModelTest` — 图表 ViewModel 🆕

**测试数: 8 | 状态: ✅ 全部通过**

| 类别 | 用例数 | 说明 |
|------|--------|------|
| **初始状态** | 2 | showCost=false、timeRange=MONTH |
| **交互** | 2 | toggle showCost、setTimeRange |
| **天气集成** | 4 | MONTH获取天气、ALL清空天气、回退预报、刷新 |

---

### 11. `SmartInputParserTest` — 智能输入解析（既有）

**测试数: 41 | 状态: ✅ 全部通过**

11种正则模式、极端输入（乱码、空行、超大值、负数、emoji、中文数字、制表符、月份边界等）、自适应阈值分类。

---

### 12. `AnomalyDetectorTest` — 异常检测（既有）

**测试数: 3 | 状态: ✅ 全部通过**

读数回退拦截、500% 日增幅告警、正常日增幅接受。

---

### 13. `CostEngineTest` — 费用计算（既有）

**测试数: 4 | 状态: ✅ 全部通过**

阶梯电费叠加、三级阶梯触发、一级内无附加费、阶梯水费。

---

### 14. `ExampleUnitTest` — 占位测试

**测试数: 1 | 状态: ✅ 全部通过**

遗留模板测试。

---

## 覆盖率总结

| 模块/类 | 测试数 |
|---------|--------|
| `data/WeatherRepository` | 20 ✅ |
| `data/WeatherInterpolator` | 17 ✅ |
| `data/PredictiveAnalyzer` | 16 ✅ |
| `data/EventImpactAnalyzer` | 9 ✅ |
| `data/AdaptiveClassifier` | 8 ✅ |
| `data/DeepSeekRepository` | 8 ✅ |
| `data/ThemeDistRepository` | 10 ✅ |
| `data/UserPreferences` | 14 ✅ |
| `data/MeterRepository` | 14 ✅ |
| `data/SmartInputParser` | 41 ✅ |
| `data/AnomalyDetector` | 3 ✅ |
| `data/CostEngine` | 4 ✅ |
| `ui/chart/ChartViewModel` | 8 ✅ |
| 剩余 UI 层（ChartScreen 等） | ⚠️ 需 Compose instrumentation |

---

## 基础设施变更

| 依赖 | 版本 | 用途 |
|------|------|------|
| `io.mockk:mockk` | 1.13.12 | 通用 Mock 框架（DAO、Preferences、HttpClient） |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.7.3 | 协程测试（runTest、TestDispatcher） |
| `io.ktor:ktor-client-mock` | 2.3.7 | Ktor HTTP mock 引擎 |

## 生产代码变更

- `PredictiveAnalyzer.predictMonth` — 新增 `now: LocalDateTime` 默认参数，消除时间耦合

---

## 测试运行命令

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "*PredictiveAnalyzerTest*"
./gradlew :app:testDebugUnitTest --tests "*ChartViewModelTest*"
```
