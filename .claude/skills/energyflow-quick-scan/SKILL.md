---
name: energyflow-quick-scan
description: Pre-commit quick scan — check for common errors, style issues, KMP boundary violations, missing tests
---

# EnergyFlow — Pre-Commit Quick Scan

**Use when**: Final check before committing code — find obvious issues in 5 minutes.

## Scan Checklist (By Priority)

### 1. KMP Boundary (Highest Priority)
```bash
# Check if shared module mistakenly uses java.time or android.*
grep -rn "java\.time\|android\.\|java\.util\." shared/src/commonMain/ --include="*.kt"
```
- ✅ Should be 0 results
- ❌ If results found → replace with kotlinx.datetime / kotlinx.*

### 2. Hardcoded Colors
```bash
# Check for hardcoded hex colors (should use theme colors)
grep -rn "Color(0x[0-9A-Fa-f]\{6,8\})" app/src/main/java/com/example/energyflow/ui/ --include="*.kt"
```
- ✅ May be 0 results (or colors passed via function parameters)
- ❌ If new hardcoding found → replace with theme colors (`ElectricColor`, `DarkBackground` etc.)
- ⚠️ Legacy code like `DarkCard = Color(0xFF212538)` definitions in `Color.kt` are allowed

### 3. State Collection
```bash
# Check for collectAsState() instead of collectAsStateWithLifecycle()
grep -rn "\.collectAsState()" app/src/main/java/com/example/energyflow/ui/ --include="*.kt"
```
- ChartScreen is known to use `collectAsState()` (legacy inconsistency)
- New code must use `collectAsStateWithLifecycle()`

### 4. Font
```bash
# Check if new code uses default font
rg -n 'fontFamily\s*=' app/src/main/java/com/example/energyflow/ui/ | rg -v 'fontFamily\s*=\s*MonoFontFamily'
```
- ✅ All `fontFamily` should be `MonoFontFamily` or not need explicit setting

### 5. Null Safety
```bash
# Check for non-null assertions on MeterRecord fields (may crash)
grep -rn "electricTotal!!\|electricPeak!!\|electricValley!!\|waterTotal!!\|gasTotal!!" app/src/ --include="*.kt"
```
- ✅ Should be 0 uses of `!!`
- ❌ If found → replace with `?: 0.0` or `?: null` safe handling

### 6. Test Coverage
Quick check if new/modified public functions have corresponding tests:
```bash
# See which files were changed
git diff --name-only
# For files under data/, check if app/src/test/ has corresponding tests
```

## Quick Fix Common Issues

| Issue | Quick Fix |
|-------|----------|
| Used `#XXXXXX` color | Check `.claude/docs/ui-layer/theme-and-navigation.md` color table for corresponding theme color |
| Used `java.time` in shared | Change to `kotlinx.datetime`, convert in Android wrapper if needed |
| Used `collectAsState()` | Change to `collectAsStateWithLifecycle()` |
| Used `!!` null assertion | Change to `?: 0.0` / `?: return` / `?.let{}` |
| No tests written | Add at least edge case tests in corresponding test file |
| Changed public API | Confirm all callers are updated, run all tests |

## Output Format

After scan completes, output results in this format:
```
## Quick Scan Results

### 🔴 Critical (Must Fix)
- file.kt:42 — Used java.time in shared module

### 🟡 Warning (Recommended Fix)
- file.kt:88 — New code without tests

### 🟢 Info (Confirmed Compliant)
- KMP boundary: ✅
- Color convention: ✅
- Font: ✅
```

## Related Skills
- Run tests: `energyflow-test` — run tests before scanning
- Commit: `energyflow-commit` — standardized commit after scan passes
- Security: `energyflow-security` — security-specific checks
