---
name: energyflow-onboard
description: 新贡献者引导——从零到能提交第一个PR的完整路径
---

# EnergyFlow — 新贡献者引导

**用途**: 新人加入项目 / 第一次看代码 / 需要快速上手时使用。

## 5分钟快速概览

EnergyFlow 是一个**家庭能耗记录 Android App**（Kotlin + Compose + Hilt + Room + KMP），用于：
- 📝 记录电表/水表/燃气表读数（支持 11 种自然语言输入格式）
- 💰 计算阶梯电价费用（峰谷分时 + 阶梯加价）
- 📊 可视化能耗趋势 + 月度预测（双重指数平滑算法）
- 🌱 碳足迹追踪 + 绿色徽章
- 🤖 AI 智能分析（DeepSeek）+ 天气集成（Open-Meteo）

## 第一小时：建立心智模型

### 1. 读核心文档（30分钟）
按顺序：
1. `.claude/docs/architecture/overview.md` — 项目全景图（10分钟）
2. `.claude/docs/architecture/gotchas.md` — 必知的陷阱（10分钟）
3. `.claude/docs/architecture/adr-001-tab-navigation.md` — 架构决策（5分钟）
4. `.claude/docs/architecture/adr-002-hilt-wrapper-pattern.md` — 分层模式（5分钟）

### 2. 理解数据流（15分钟）
```
用户输入 "7.15 14.30 16639 880"
  → SmartInputParser (正则解析 + AI 降级)
    → AnomalyDetector (单调递增 + 突增校验)
      → MeterRepository → Room 数据库
        → ChartViewModel (图表聚合 + 预测 + 账单 + 碳足迹)
          → ChartScreen / MainScreen (Compose UI)
```

### 3. 运行起来（15分钟）
```bash
# 编译
./gradlew :app:compileDebugKotlin

# 测试
./gradlew :app:testDebugUnitTest

# 构建 APK（需要 Android SDK）
./gradlew :app:assembleDebug
```

## 第一个 PR：改一个简单的东西

### 推荐入门任务
1. **修复一个 "info" 级别的 code review finding** — 改动小，风险低
2. **给一个已有函数加单元测试** — 熟悉测试模式
3. **修复一个 `collectAsState()` → `collectAsStateWithLifecycle()`** — 学习 Compose 生命周期
4. **给一个数据类加注释** — 理解数据模型

### 流程
```bash
# 1. 建分支
git checkout -b fix/simple-thing

# 2. 改代码（读 CLAUDE.md + 对应 skill）

# 3. 验证
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest

# 4. 提交
git commit -m "fix(ui): collectAsState → collectAsStateWithLifecycle in XxxScreen"

# 5. Push & 创建 PR
```

## 关键联系人/资源

| 需要什么 | 去哪找 |
|---------|--------|
| 理解架构 | `.claude/docs/architecture/` |
| 理解数据模型 | `.claude/docs/data-layer/meter-record.md` |
| 理解 UI 组件 | `.claude/docs/ui-layer/` |
| 知道怎么测试 | `.claude/docs/testing/` / `energyflow-test` skill |
| 知道怎么提交 | `energyflow-commit` skill |
| 提交前检查 | `energyflow-quick-scan` skill |
| 遇到 Bug | `energyflow-diagnose` skill |
| 要重构 | `energyflow-refactor` skill |

## 常见新手陷阱

| 陷阱 | 为什么容易犯错 | 正确做法 |
|------|-------------|---------|
| 用 `java.time` in shared | IDE 自动导入 | 只用 `kotlinx.datetime` |
| 硬编码颜色 | 看起来方便 | 查主题色表 |
| `electricPeak!!` 崩溃 | 以为一定有值 | 用 `?: 0.0` |
| 减法方向反了 | 直觉是消费量 | 读数是累计值，大减小 |
| 改了 shared 没编译检查 | 只编译了 app | `./gradlew :shared:compileDebugKotlinAndroid` |
> 更多陷阱 → `.claude/docs/architecture/gotchas.md`

## 相关 Skills
- 认知引导: `energyflow-acknowledge` — 深度架构理解
- 第一个PR: `energyflow-new-feature` — 从需求到验证的完整路径
