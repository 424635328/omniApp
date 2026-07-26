# 测试流程 (Test Process)

## 开发流程

```
需求 → 设计 → 编码 → 测试 → Review → 合并 → 发布
                    ↑
              TDD 可选: 先写测试再写实现
```

### 1. 编写测试

#### 何时写测试
- **必须**: 新增业务逻辑（引擎、解析器、检测器）
- **必须**: 修复 Bug（写回归测试）
- **应该**: 修改现有逻辑（确保不破坏）
- **可选**: UI 组件（Compose 测试成本高）

#### 测试命名规范
```kotlin
// ✅ 好的命名：描述行为
@Test
fun `electric tiers apply additive surcharge`() { ... }

@Test
fun `decreasing electric total is filtered out`() { ... }

@Test
fun `empty records returns null`() { ... }

// ❌ 差的命名：描述实现
@Test
fun `testCalculate`() { ... }

@Test
fun `testCase1`() { ... }
```

#### 测试结构 (AAA 模式)
```kotlin
@Test
fun `water price follows configured tiers`() {
    // Arrange — 准备数据
    val rules = BillingRules(
        waterTier1Limit = 10.0,
        waterTier2Limit = 20.0,
        waterTier1Price = 2.0,
        waterTier2Price = 3.0,
        waterTier3Price = 4.0
    )

    // Act — 执行操作
    val bill = CostEngineShared.calculate(
        rules = rules, totalKwh = 0.0, waterTons = 25.0
    )

    // Assert — 验证结果
    assertEquals(70.0, bill.waterTotalCost, 0.001)
    assertEquals(2.8, bill.waterPrice, 0.001)
}
```

### 2. 运行测试

```bash
# 全部测试（app 模块）
./gradlew :app:testDebugUnitTest

# 共享模块 commonTest（SharedEnginesTest）
# 注意：shared/build.gradle.kts 声明的是 jvm("desktop")，任务名是 desktopTest，不是 jvmTest
./gradlew :shared:desktopTest

# 单个测试类
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest"

# 单个测试方法
./gradlew :app:testDebugUnitTest --tests "com.example.energyflow.data.CostEngineTest.electric tiers apply additive surcharge"

# 带详细输出
./gradlew :app:testDebugUnitTest --info

# 生成 HTML 报告
./gradlew :app:testDebugUnitTest
# 报告位置: app/build/reports/tests/testDebugUnitTest/index.html
```

> 涉及 `shared/src/commonMain` 的改动，除 app 测试外还必须运行 `./gradlew :shared:desktopTest`。

### 3. 调试测试失败

#### 步骤
1. **读错误信息** — assertEquals 的 expected vs actual 通常足够
2. **读测试代码** — 理解测试意图
3. **读源代码** — 找到被测逻辑
4. **检查测试数据** — 工厂函数是否正确
5. **检查时间依赖** — 是否用了 LocalDateTime.now()
6. **检查 Mock** — MockK 的 coEvery 是否正确

#### 常见失败原因
| 症状 | 可能原因 |
|------|---------|
| assertEquals 差值很小 | 浮点精度，调整 delta |
| assertEquals 差值很大 | 逻辑错误或测试数据错误 |
| NullPointerException | Mock 不完整，某些方法未 mock |
| ClassCastException | 类型不匹配 |
| 测试通过但实际失败 | 测试没有覆盖真实场景 |

### 4. 代码 Review 中的测试检查

Review 时检查：
- [ ] 新增逻辑是否有测试？
- [ ] 测试是否覆盖了边界条件？
- [ ] 测试命名是否清晰？
- [ ] 是否有时间依赖？（应使用固定时间）
- [ ] Mock 是否正确？（coEvery vs every）
- [ ] 断言是否精确？（delta 是否合理）

## TDD 流程（可选）

```
1. 写一个失败的测试
2. 写最少代码让测试通过
3. 重构代码（测试仍然通过）
4. 重复
```

### TDD 示例
```kotlin
// Step 1: 写失败的测试
@Test
fun `zero usage has zero cost`() {
    val bill = CostEngineShared.calculate(rules = BillingRules(), totalKwh = 0.0)
    assertEquals(0.0, bill.electricTotalCost, 0.001)
}

// Step 2: 写代码让测试通过
fun calculate(...): BillResult {
    val safeTotal = totalKwh.coerceAtLeast(0.0)
    // ...
}

// Step 3: 重构（测试仍然通过）
```

## 测试维护

### 何时删除测试
- 被测代码被删除
- 测试覆盖的功能被移除

### 何时修改测试
- 被测逻辑的预期行为改变
- 测试数据过时（如硬编码的年份）
- 测试因实现细节而非行为变化而失败

### 何时不修改测试
- 实现重构但行为不变（测试应该仍然通过）
- 测试因浮点精度偶尔失败（调整 delta）

## 测试报告

### 单元测试报告
位置: `app/build/reports/tests/testDebugUnitTest/index.html`

### 覆盖率报告（未来）
需要配置 JaCoCo 插件：
```kotlin
// build.gradle.kts
plugins {
    id("jacoco")
}
```
