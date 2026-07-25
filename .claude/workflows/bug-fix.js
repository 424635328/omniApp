export const meta = {
  name: 'bug-fix',
  description: 'Structured bug fix: parallel layer diagnosis → root cause → fix → verify',
  phases: [
    { title: 'Reproduce', detail: 'Write failing regression test' },
    { title: 'Diagnose', detail: 'Parallel multi-layer root cause analysis' },
    { title: 'Fix', detail: 'Apply minimal fix and confirm test passes' },
    { title: 'Verify', detail: 'Full suite + boundary checks' }
  ]
}

// Shared prompt blocks — canonical source: .claude/shared/rules.md
const RULES = `CRITICAL RULES:
1. KMP: shared/src/commonMain/ MUST NOT import java.time, java.util.*, or android.*
2. Compose: ALL colors → theme colors (ElectricColor, etc.) — NO Color(0xFF...)
3. Font: MonoFontFamily everywhere
4. State: collectAsStateWithLifecycle(), not collectAsState()
5. Hilt: @Singleton class X @Inject constructor(deps)
6. Data: MeterRecord fields nullable → ?: 0.0, never !!
7. Minimal: no abstractions for single use
8. Surgical: don't modify unrelated adjacent code`

const VERIFY = `./gradlew :app:compileDebugKotlin
./gradlew :shared:compileDebugKotlinAndroid (if shared changed)
./gradlew :app:testDebugUnitTest
grep -rn "java\\.time\\|java\\.util\\|android\\." shared/src/commonMain/ --include="*.kt" → must be 0
grep -rn "Color(0x" app/src/.../ui/ --include="*.kt" | grep -v "Color.kt"
grep -rn "collectAsState()" app/src/.../ui/ --include="*.kt" | grep -v ChartScreen
grep -rn "!!" <changed files>`

const bug = args?.bug || args?.description || 'the reported bug'

phase('Reproduce')

const repro = await agent(
  `Write a FAILING test that reproduces this bug: "${bug}"

Read the relevant source files and existing tests. Write a FAILING test that reproduces the bug.

The test should:
- Be in the correct test file (app/src/test/java/com/example/energyflow/...)
- Follow the project test conventions (JUnit 4, backtick method names, AAA pattern)
- Use fixed time (not LocalDateTime.now()) if time-dependent
- Use MockK for DAO/preferences if needed, or inline data for pure logic

Run: ./gradlew :app:testDebugUnitTest --tests "<your test>"
Expected: the test FAILS (confirming the bug exists).`,
  { label: 'write-repro-test' }
)

log(repro?.substring(0, 300) || 'reproduction test written')

phase('Diagnose')

// Parallel multi-layer root cause analysis
const LAYERS = [
  {
    key: 'data',
    prompt: `Diagnose data-layer root cause for: "${bug}"
Check app/src/main/java/com/example/energyflow/data/
- Null handling: MeterRecord fields nullable → no !! assertions
- Cumulative vs delta direction (current - previous, large minus small)
- Room queries, DataStore reads
- SmartInputParser year assumption, AdaptiveClassifier thresholds`
  },
  {
    key: 'shared',
    prompt: `Diagnose shared/KMP root cause for: "${bug}"
Check shared/src/commonMain/
- java.time or android.* violations (MUST use kotlinx.datetime)
- Algorithm errors in CostEngineShared, PredictiveAnalyzerShared, AnomalyDetectorShared
- Edge cases: zero values, null handling, boundary conditions`
  },
  {
    key: 'ui',
    prompt: `Diagnose UI-layer root cause for: "${bug}"
Check app/src/main/java/com/example/energyflow/ui/
- collectAsState() vs collectAsStateWithLifecycle()
- Hardcoded hex colors (should use ElectricColor etc.)
- Font: MonoFontFamily everywhere
- State management, lifecycle issues, recomposition`
  },
  {
    key: 'di',
    prompt: `Diagnose DI/infra root cause for: "${bug}"
Check:
- Hilt modules and @Inject/@Provides setup
- Build cache issues (ClassNotFoundException → --rerun-tasks)
- Gradle configuration, dependency conflicts
- Room schema version and destructive migration`
  }
]

const layerDiags = await parallel(
  LAYERS.map(l => () => agent(
    `${l.prompt}
Output: root cause hypothesis with file:line reference, or "NOT IN THIS LAYER" if the bug isn't here.`,
    { label: `diag:${l.key}`, phase: 'Diagnose' }
  ))
)

const relevantDiags = layerDiags.filter(Boolean).filter(d => !d.includes('NOT IN THIS LAYER'))
log(`Layer diagnosis: ${relevantDiags.length}/${LAYERS.length} layers relevant`)

// Synthesize parallel diagnoses into one root cause
const diagnosis = await agent(
  `Synthesize these parallel layer diagnoses into ONE root cause analysis:

${relevantDiags.map((d, i) => `## Layer ${i + 1}\n${d}`).join('\n\n')}

Output:
1. PRIMARY ROOT CAUSE (file:line — what's actually wrong)
2. Contributing factors (if any)
3. Why the existing code behaves this way
4. Which layer the fix should be in
5. Specific fix approach`,
  { label: 'synthesize-diagnosis', phase: 'Diagnose' }
)

log(diagnosis?.substring(0, 500) || 'diagnosis complete')

phase('Fix')

const fix = await agent(
  `Apply the MINIMAL fix for this diagnosed bug:

DIAGNOSIS:
${diagnosis}

${RULES}

After the fix, run: ./gradlew :app:compileDebugKotlin
Then run the reproduction test: ./gradlew :app:testDebugUnitTest --tests "<test name>"
Expected: PASS (bug is fixed)

If the test still fails, re-examine the diagnosis.`,
  { label: 'apply-fix', agentType: 'bug-fixer' }
)

log(fix?.substring(0, 300) || 'fix applied')

phase('Verify')

const verify = await agent(
  `Verify the bug fix:

1. Compile: ./gradlew :app:compileDebugKotlin
2. Shared (if changed): ./gradlew :shared:compileDebugKotlinAndroid
3. Full test suite: ./gradlew :app:testDebugUnitTest
4. KMP boundary: grep -rn "java\\.time\\|android\\." shared/src/commonMain/ --include="*.kt" → should be 0
5. Color check: grep -rn "Color(0x" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v "Color.kt"
6. State check: grep -rn "collectAsState()" <new ui files> | grep -v ChartScreen
7. Null safety: grep -rn "!!" <changed files>

Boundary check — edge cases:
- Null values? Zero values? Empty lists? Extreme values?
- Cross-month / cross-year boundaries?

Report: PASS/FAIL for each check. If all PASS, the fix is verified.`,
  { label: 'full-verify' }
)

log(verify?.substring(0, 500) || 'verification complete')

return {
  reproduction: repro,
  diagnosis,
  verification: verify
}
