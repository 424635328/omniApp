# Shared Rules — EnergyFlow 单一事实源

> 所有 agent、skill、workflow 的铁律和已知忽略的唯一权威来源。
> 修改规则时只需改这一个文件。其他文件引用此文件即可。

---

## 铁律（5条 — 违反必错）

1. **KMP 边界**: `shared/src/commonMain/` 禁止 `java.time` / `android.*` / `java.util.*` — 只用 `kotlinx.datetime`
2. **Hilt**: Engine = `@Singleton class X @Inject constructor(deps)` | VM = `@HiltViewModel`
3. **Compose**: `MonoFontFamily` 全局字体 | 颜色只用主题色(禁止 `Color(0xXX..)`) | Flow 用 `collectAsStateWithLifecycle()`
4. **Data**: `MeterRecord` 字段全 nullable（`!!` 禁止，用 `?: 0.0`）| 读数是累计值(大减小=消耗) | `null`≠`0.0`
5. **Room**: `fallbackToDestructiveMigration()` — schema变更=数据全清（开发阶段有意）

## 已知忽略（3条 — 不要再报告）

- `NeonYellow = #00A8FF` — 遗留命名（实际是蓝色，Color.kt 别名指向 ElectricStart）
- `fallbackToDestructiveMigration()` — 有意为之
- `SmartInputParser` 年份假设 — 已知限制

> 历史条目 `ChartScreen.collectAsState()` 已于 2026-07-26（commit eba3f4b）修复——全 UI 已无 collectAsState()，不再豁免任何文件。

## 验证命令

```bash
./gradlew :app:compileDebugKotlin                          # 编译
./gradlew :shared:compileDebugKotlinAndroid                # KMP编译
./gradlew :app:testDebugUnitTest                           # 全量测试
./gradlew :shared:desktopTest                              # shared 测试 (commonTest/SharedEnginesTest)
grep -rn "java\.time\|android\." shared/src/commonMain/    # KMP边界
grep -rn "Color(0x" <ui files> | grep -v "Color.kt"        # 硬编码颜色
grep -rn "collectAsState()" <ui files>                      # 状态收集 (应为 0，无豁免)
grep -rn "!!" <changed files>                               # 空安全
```

## Workflow Prompt 模板

以下模板供 workflow 脚本使用。修改规则时同步更新这些模板。

### RULES_BLOCK（嵌入 Agent prompt）
```
CRITICAL RULES:
1. KMP: shared/src/commonMain/ MUST NOT import java.time, java.util.*, or android.*
2. Compose: ALL colors → theme colors (ElectricColor, etc.) — NO Color(0xFF...)
3. Font: MonoFontFamily everywhere
4. State: collectAsStateWithLifecycle(), not collectAsState()
5. Hilt: @Singleton class X @Inject constructor(deps)
6. Data: MeterRecord fields nullable → ?: 0.0, never !!
7. Minimal: no abstractions for single use
8. Surgical: don't modify unrelated adjacent code
9. Room: fallbackToDestructiveMigration() — schema变更=数据全清（开发阶段有意）
```

### VERIFY_BLOCK（嵌入 Agent prompt）
```
Verify:
1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid (if shared changed)
3. ./gradlew :app:testDebugUnitTest
4. grep -rn "java\.time\|java\.util\|android\." shared/src/commonMain/ --include="*.kt" → must be 0
5. grep -rn "Color(0x" app/src/.../ui/ --include="*.kt" | grep -v "Color.kt"
6. grep -rn "collectAsState()" app/src/.../ui/ --include="*.kt" → must be 0 (no exemptions)
7. grep -rn "!!" <changed files>
```
