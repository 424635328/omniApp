---
name: energyflow-refactor
description: 安全重构引导——依赖分析、方案设计、精准修改、验证回退
---

# EnergyFlow — 安全重构引导

**用途**: 重构 / 优化结构 / 改架构时使用。

## 核心原则

> 重构 = 改变结构，不改变行为。如果行为变了，那是"重写"而非"重构"。

## 第一步：理解现状

### 必读文档
1. `.claude/docs/architecture/overview.md` — 整体架构
2. `.claude/docs/architecture/adr-001-tab-navigation.md` — 导航设计决策
3. `.claude/docs/architecture/adr-002-hilt-wrapper-pattern.md` — 分层模式
4. `.claude/docs/architecture/adr-003-adaptive-classifier.md` — 自适应阈值
5. `.claude/docs/architecture/gotchas.md` — **必读**——避免踩坑
6. `.claude/docs/shared-kmp/module-design.md` — KMP 模块设计

### 画出依赖图
```
要重构的类 → 谁依赖它？ → 它依赖谁？
           → 哪些测试覆盖它？
           → 哪些 UI 组件使用它？
```

使用搜索工具找到所有引用：
```bash
grep -r "ClassName" --include="*.kt" app/src/
grep -r "ClassName" --include="*.kt" shared/src/
```

## 第二步：制定方案

### 回答以下问题
1. **重构范围** — 只改一个文件？一个包？跨模块？
2. **行为保证** — 如何确保重构后行为不变？（全量测试通过）
3. **回退方案** — 如果出问题，如何撤销？
4. **影响范围** — 哪些文件会受影响？多少行变更？
5. **KMP 边界** — 是否涉及 shared 模块？是否需要类型转换？

### 选择策略

| 重构类型 | 策略 | 时机 |
|---------|------|------|
| 提取方法/类 | 纯 IDE 重构 → 测试验证 | 代码重复 |
| 移动文件 | git mv → 更新 import → 测试 | 包结构不合理 |
| 改变接口 | 先加新接口 → 迁移调用方 → 删除旧接口 | 接口设计不好 |
| 改变 DI | 先验证新 Module → 切换 → 删除旧 Module | DI 过于耦合 |
| KMP 提取 | app 层写逻辑 → 测试 → 提取到 shared | 发现跨平台可复用 |

## 第三步：精准修改

### 修改规则
- [ ] 每次只做一种重构（提取 + 移动分开做）
- [ ] 每一步之间运行全量测试
- [ ] 不改行为——如果测试失败，说明你改了行为
- [ ] 不改相邻代码——保持 diff 只包含重构
- [ ] 改完后运行 `grep` 确认没有遗留旧引用

### KMP 提取流程（特例）
```
1. 在 shared 模块创建纯逻辑类
2. 从 app 层复制逻辑 → 替换 java.time 为 kotlinx.datetime
3. 在 app 层创建 Hilt wrapper（@Singleton + @Inject）
4. 更新 app 层调用方 → 编译 → 测试
5. 删除 app 层的旧实现
```

## 第四步：验证

```bash
# 1. 全量编译
./gradlew :app:compileDebugKotlin
./gradlew :shared:compileDebugKotlinAndroid

# 2. 全量测试（重构最重要的验证手段）
./gradlew :app:testDebugUnitTest

# 3. 确认没有死代码
grep -r "OldClassName" --include="*.kt" app/src/ shared/src/
```

### 验证清单
- ✅ 全量测试通过（与重构前一致）
- ✅ 没有新的编译警告
- ✅ 没有遗留的旧 import / 旧类名引用
- ✅ KMP 边界正确（shared 无 android.* / java.*）
- ✅ git diff 只包含结构性变更，无行为变更

## 禁止事项

- ❌ 不要"顺便"改行为——行为和结构分开提交
- ❌ 不要跳过全量测试——这是唯一的安全网
- ❌ 不要同时重构 + 加功能——分两次 PR
- ❌ 不要删除可能被外部引用的 public API（除非确认没有调用方）
- ❌ 不要推翻 ADR 中的架构决策而不先讨论
- ❌ 不要用 NavHost 替代 AnimatedContent——这是有意的设计决策

## 相关 Skills
- 跑测试: `energyflow-test` — 重构后必须全量测试
- 提交: `energyflow-commit` — 重构用 refactor type
- 数据迁移: `energyflow-data-migration` — 如果涉及 schema 变更
