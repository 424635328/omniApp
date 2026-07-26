# 开发流程 (Development Workflow)

## 分支策略

```
main (稳定分支)
├── feature/xxx (功能分支)
├── fix/xxx (修复分支)
└── refactor/xxx (重构分支)
```

### 命名规范
- `feature/add-water-prediction` — 新功能
- `fix/wrong-tier-calculation` — Bug 修复
- `refactor/extract-billing-logic` — 重构

## 提交规范 (Conventional Commits)

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Type
| Type | 用途 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不改变行为） |
| `test` | 添加/修改测试 |
| `docs` | 文档更新 |
| `chore` | 构建/工具/依赖 |
| `style` | 代码格式（不影响逻辑） |

### Scope
| Scope | 用途 |
|-------|------|
| `data` | 数据层 |
| `ui` | UI 层 |
| `shared` | KMP 共享模块 |
| `di` | 依赖注入 |
| `test` | 测试 |

### 示例
```
feat(data): add water prediction to ChartViewModel
fix(ui): correct NeonYellow color alias (was blue, not yellow)
refactor(shared): extract billing logic to CostEngineShared
test(data): add edge cases for SmartInputParser
docs: update gotchas with color system details
```

## PR 流程

### 1. 创建 PR
```
标题: feat(data): add water prediction
描述:
- 实现了水表月度预测
- 使用简单平均法（非 DES）
- 天气高温时上浮 10%
- 新增 WaterPredictionPanel UI
```

### 2. Code Review 检查清单

#### 必须检查
- [ ] 编译通过: `./gradlew :app:compileDebugKotlin`
- [ ] 测试通过: `./gradlew :app:testDebugUnitTest`
- [ ] 新增逻辑有测试覆盖
- [ ] 无硬编码的 hex 颜色值（使用主题色）
- [ ] KMP 边界正确（shared 无 java.time）

#### 应该检查
- [ ] 测试命名清晰描述行为
- [ ] 边界条件有测试
- [ ] 错误路径有测试
- [ ] 无未使用的 import

#### 建议检查
- [ ] 代码风格与现有代码一致
- [ ] 注释解释 "为什么" 而非 "是什么"
- [ ] 无过度工程

### 3. 合并条件
- 至少 1 人 approve
- 所有 CI 检查通过
- 无 merge conflict

## CI/CD 流程

### 当前（手动）
```bash
# 1. 编译检查
./gradlew :app:compileDebugKotlin

# 2. 单元测试（app 模块）
./gradlew :app:testDebugUnitTest

# 3. 共享模块 commonTest（SharedEnginesTest）
#    任务名是 desktopTest（shared/build.gradle.kts 声明 jvm("desktop")），不是 jvmTest
./gradlew :shared:desktopTest

# 4. 构建 APK
./gradlew :app:assembleDebug
```

### 未来（GitHub Actions）
```yaml
name: CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      - run: ./gradlew :app:testDebugUnitTest
      - run: ./gradlew :app:compileDebugKotlin
```

## 代码质量工具

### 当前使用
- **JUnit 4** — 单元测试框架
- **MockK** — Mock 框架
- **kotlinx-coroutines-test** — 协程测试

### 建议添加
- **JaCoCo** — 测试覆盖率
- **Detekt** — 静态代码分析
- **ktlint** — 代码格式化

## 发布流程

### Debug 发布
```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Release 发布（未来）
```bash
./gradlew :app:assembleRelease
# 需要签名配置
```

## 热修复流程

### 紧急修复
```
1. 从 main 创建 fix/xxx 分支
2. 写回归测试（先失败）
3. 修复代码（测试通过）
4. 创建 PR（标记为 hotfix）
5. 快速 review + 合并
6. 部署
```

### 回滚
```bash
git revert <commit-hash>
git push origin main
```
