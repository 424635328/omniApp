---
name: energyflow-acknowledge
description: 全面理解 EnergyFlow 代码库——项目结构、架构决策、数据流、已知陷阱
---

# EnergyFlow — 代码库认知引导

**用途**: 理解项目 / 探索代码 / 不知道干什么时使用。

## 第一步：加载知识上下文

按顺序读取以下文档（不要跳过）：

### 必读（核心理解）
1. `.claude/docs/architecture/overview.md` — 项目身份、技术栈、模块结构、数据流、文件参考表
2. `.claude/docs/architecture/gotchas.md` — **最重要**——反直觉的坑、命名陷阱、边界条件
3. `.claude/docs/architecture/adr-001-tab-navigation.md` — 为什么不用 NavHost
4. `.claude/docs/architecture/adr-002-hilt-wrapper-pattern.md` — KMP 与 Android 的分层模式
5. `.claude/docs/architecture/adr-003-adaptive-classifier.md` — 自适应分类阈值

### 按需读取（深入理解）
- 数据模型: `meter-record.md` → 计费: `cost-engine.md` → 解析: `smart-input-parser.md`
- 异常检测: `anomaly-detector.md` → 预测: `predictive-analyzer.md`
- 碳足迹: `carbon-and-insight.md` → KMP: `module-design.md`
- UI: `theme-and-navigation.md` / `chart-screen.md` / `settings-and-reports.md`
- 外部: `external-services.md` → DI: `app-entry-and-di.md`
- 构建: `build-and-test.md` → 测试: `strategy.md` / `test-cases.md` / `process.md`

## 第二步：建立心智模型

阅读完毕后，确保能回答：
- **数据流**: `User Input → SmartInputParser → AnomalyDetector → MeterRepository → Room → ChartViewModel/InsightGenerator/PredictiveAnalyzer`
- **分层**: shared = 纯逻辑+kotlinx.datetime | app = Hilt DI+java.time+Android API
- **为什么 AnimatedContent 而非 NavHost?** ViewModel 常驻、即时切换、无销毁重建
- **为什么电表读数是累计值?** 抄表直接读电表数字，差值为消耗量
- **为什么 NeonYellow 是蓝色?** 遗留命名，实际指向 ElectricStart (#00A3FF)

## 第三步：输出结构化理解

输出格式（不要直接 dump 文档原文）：
```markdown
## 项目概览
- 身份: [一句话]
- 核心领域: [计费/预测/碳足迹/...]
- 关键设计模式: [Hilt Wrapper / Adaptive Learning / Smart Parse + AI Fallback]

## 我关心的模块
- 文件: [路径]  职责: [一句话]  陷阱: [相关 gotcha]

## 关键边界
- KMP: shared 禁止 java.time / android.*
- Hilt: Engine 用 @Singleton class X @Inject constructor
- Compose: MonoFontFamily / 只用主题色 / collectAsStateWithLifecycle()
```

## 铁律
- ✅ 读 gotchas.md ——每次都要读
- ✅ 理解 ADR ——设计决策有其原因
- ❌ 不要在本阶段写代码——这是理解阶段
- ❌ 不要跳过 gotchas.md——它是血的教训的合集

## 相关 Skills
- 新人上手: `energyflow-onboard` — 更简洁的入门路径
- 数据迁移: `energyflow-data-migration` — 理解 Room/DataStore 结构
- 运行测试: `energyflow-test` — 熟悉测试体系
