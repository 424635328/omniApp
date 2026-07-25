---
name: energyflow-diagnose
description: Bug 诊断引导——系统化排查、定位和修复问题
---

# EnergyFlow — Bug 诊断引导

**用途**: 修 bug / 排查 / 报错 / 不工作时使用。

## 第零步：先排除已知陷阱

**在深入排查前，先检查 gotchas.md 中最常见的坑：**

| 症状 | 最常见原因 | 快速验证 |
|------|-----------|---------|
| `ClassNotFoundException` | Hilt 构建缓存投毒 | `./gradlew :app:assembleDebug --rerun-tasks` |
| 数据全丢了 | Room destructive migration | 检查 schema 版本是否 +1 |
| 计费结果不对 | 计费版本迁移重置了规则 | 检查 `CURRENT_BILLING_VERSION` |
| 峰谷电费为 0 | 峰谷值都为 null | 检查记录是否有 peak/valley |
| 解析结果错误 | AdaptiveClassifier 阈值漂移 | 检查分类阈值 |
| BuildConfig 找不到 | 未启用 AGP build features | `buildFeatures { buildConfig = true }` |

## 第一步：复现 [Reproduce]

1. **精确复现步骤** — 什么操作、什么输入触发了 bug？
2. **最小化输入** — 能用最少的数据复现吗？
3. **写出复现测试** — 在对应测试类中写一个失败的测试

```kotlin
@Test
fun `reproduce bug: decreasing record not flagged`() {
    // Arrange — 模拟触发 bug 的数据
    val records = listOf(...)
    // Act
    val result = detector.checkElectricMonotonic(...)
    // Assert — 当前应该失败（bug 还存在）
    assertNotNull(result) // 预期有警告但没有 → 这就是 bug
}
```

## 第二步：分类 [Classify]

确定 bug 属于哪一层：

| 层次 | 表现 | 排查重点 |
|------|------|---------|
| **shared (KMP)** | 计算结果异常 | 纯逻辑问题，直接用测试验证 |
| **data (Android)** | 数据存储/读取异常 | Hilt 注入、Room 查询、DataStore |
| **ui (Compose)** | 界面显示异常 | StateFlow 收集、主题色、生命周期 |
| **DI/infra** | 启动崩溃/注入失败 | Hilt 配置、模块安装 |
| **external** | API 调用失败 | 网络/反序列化/降级逻辑 |

## 第三步：定位 [Locate]

### 按层次排查

**shared 纯逻辑** → 直接构造测试数据，单步调试：
```kotlin
val result = CostEngineShared.calculate(rules = ..., totalKwh = 300.0)
println("totalCost=${result.electricTotalCost}") // 对比预期
```

**data 层** → 检查数据流：
```kotlin
// 打印中间状态
val parsed = parser.parseWithContext(input)
println("parsed=$parsed")
val warning = detector.checkElectricMonotonic(value, timestamp)
println("warning=$warning")
```

**ui 层** → 检查状态收集和重组：
- 是否用了 `collectAsState()` 而非 `collectAsStateWithLifecycle()`？
- 是否在 Composition 外修改了 State？
- Theme 色是否在暗/亮模式下正确？

### 常见错误模式速查

| 代码模式 | 问题 | 正确写法 |
|---------|------|---------|
| `collectAsState()` | 不尊重生命周期 | `collectAsStateWithLifecycle()` |
| `#XXXXXX` 硬编码颜色 | 不支持主题切换 | `ElectricColor` / `DarkBackground` 等 |
| `java.time.*` in shared | 破坏 KMP 兼容 | `kotlinx.datetime.*` |
| `val x = dao.getAll().first()` | 在非协程上下文调用 | `suspend fun` 或 Flow collect |
| `electricPeak!!` | 峰电可能为 null | `electricPeak ?: 0.0` |
| `newVal - prevVal` (减法反了) | 读数是累计值 | `current - previous` (大减小区) |

## 第四步：修复 [Fix]

1. **写回归测试先** — 确认测试失败 → 修复 → 测试通过
2. **最小改动** — 只改修复 bug 所需的代码
3. **不要顺手重构** — 保持 diff 干净
4. **检查边界** — 考虑 null / 0 / 极端值

## 第五步：验证 [Verify]

```bash
# 1. 回归测试
./gradlew :app:testDebugUnitTest --tests "YourBugTest"

# 2. 全量测试（确认没有破坏其他功能）
./gradlew :app:testDebugUnitTest

# 3. 编译检查
./gradlew :app:compileDebugKotlin
```

### 验证清单
- ✅ 新增的回归测试通过
- ✅ 全部已有测试通过
- ✅ 编译成功
- ✅ 如果可能，在设备上手动验证修复效果

## 特殊场景

### Hilt / DI 问题
```
1. clean build: ./gradlew clean
2. 清除 Gradle cache: rm -rf ~/.gradle/caches
3. 重建: ./gradlew :app:assembleDebug --rerun-tasks
```

### Room 数据问题
```
1. 检查 schema version (AppDatabase.kt)
2. 检查 TypeConverters 是否正确注册
3. 检查 DAO 查询 SQL 是否正确
4. 用 App Inspection 或 uninstall 清除数据
```

### KMP 编译问题
```bash
./gradlew :shared:compileDebugKotlinAndroid --info  # 详细日志
```

## 相关 Skills
- 跑测试: `energyflow-test` — 运行回归测试和调试失败
- 编译问题: `energyflow-build-debug` — Gradle/Hilt/编译专项排查
- 预检: `energyflow-quick-scan` — 修复后做一轮快速扫描
