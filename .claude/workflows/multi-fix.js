export const meta = {
  name: 'multi-fix',
  description: 'Parallel multi-bug fix: diagnose all bugs in parallel → fix each with pipeline → verify all',
  phases: [
    { title: 'Parse', detail: 'Extract bug list from args' },
    { title: 'Diagnose', detail: 'All bugs diagnosed in parallel, each with multi-layer analysis' },
    { title: 'Fix', detail: 'Pipeline: each bug diagnosed→fixed→verified, no barrier between bugs' },
    { title: 'Synthesize', detail: 'Merge results, run full test suite once' }
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

phase('Parse')

// Accept bugs as array or semicolon-separated string
let bugs = []
if (Array.isArray(args?.bugs)) {
  bugs = args.bugs.map((b, i) => typeof b === 'string' ? { id: i + 1, description: b } : b)
} else if (typeof args?.bugs === 'string') {
  bugs = args.bugs.split(';').filter(Boolean).map((b, i) => ({ id: i + 1, description: b.trim() }))
} else if (typeof args?.bug === 'string') {
  bugs = [{ id: 1, description: args.bug }]
} else if (typeof args === 'string') {
  bugs = args.split(';').filter(Boolean).map((b, i) => ({ id: i + 1, description: b.trim() }))
} else {
  bugs = [{ id: 1, description: args?.description || 'the reported bug' }]
}

if (bugs.length === 0) {
  log('No bugs specified. Pass bugs as: args.bugs = ["bug1", "bug2"] or args.bugs = "bug1; bug2; bug3"')
  return { fixed: 0, results: [] }
}

log(`Processing ${bugs.length} bug(s): ${bugs.map(b => `#${b.id}: ${b.description.substring(0, 60)}`).join(', ')}`)

// ── Single bug fast path: parallel layer diagnosis ──
if (bugs.length === 1) {
  const bug = bugs[0]

  phase('Diagnose')

  const LAYERS = [
    { key: 'data', prompt: `Diagnose data-layer root cause for: "${bug.description}"
Read relevant files in app/src/main/java/com/example/energyflow/data/.
Check: null handling (MeterRecord fields nullable → no !!), cumulative vs delta direction, Room queries, DataStore` },
    { key: 'shared', prompt: `Diagnose shared/KMP root cause for: "${bug.description}"
Check shared/src/commonMain/ for: java.time violations, algorithm errors in CostEngineShared/PredictiveAnalyzerShared/AnomalyDetectorShared` },
    { key: 'ui', prompt: `Diagnose UI-layer root cause for: "${bug.description}"
Check app/src/main/java/com/example/energyflow/ui/ for: collectAsState() vs collectAsStateWithLifecycle(), hardcoded hex colors, state management, lifecycle issues` },
    { key: 'di', prompt: `Diagnose DI/infra root cause for: "${bug.description}"
Check: Hilt modules, @Inject/@Provides, build cache issues, Gradle configuration` }
  ]

  const layerDiags = await parallel(
    LAYERS.map(l => () => agent(
      `${l.prompt}
READ-ONLY analysis: use grep/read only. Do NOT edit files. Do NOT run Gradle (other diagnosis agents run concurrently — concurrent Gradle deadlocks on the project lock).
Output: root cause hypothesis with file:line reference, or "NOT IN THIS LAYER" if the bug isn't here.`,
      { label: `diag:${l.key}`, phase: 'Diagnose' }
    ))
  )

  const relevantDiags = layerDiags.filter(Boolean).filter(d => !d.includes('NOT IN THIS LAYER'))
  log(`Layer diagnosis: ${relevantDiags.length}/${LAYERS.length} layers have relevant findings`)

  const diagnosis = await agent(
    `Synthesize these parallel layer diagnoses into ONE root cause analysis:
${relevantDiags.map((d, i) => `## Layer ${i + 1}\n${d}`).join('\n\n')}

Output:
1. PRIMARY ROOT CAUSE (with file:line)
2. Contributing factors (if any)
3. Recommended fix location and approach`,
    { label: 'synthesize-diagnosis', phase: 'Diagnose' }
  )

  log(diagnosis?.substring(0, 400) || 'diagnosis complete')

  phase('Fix')

  const fix = await agent(
    `Apply the MINIMAL fix for this diagnosed bug:

DIAGNOSIS:
${diagnosis}

${RULES}

After fixing, output:
- FIXED: file:line — what was changed
- COMPILE: PASS/FAIL`,
    { label: 'apply-fix', phase: 'Fix', agentType: 'bug-fixer' }
  )

  log(fix?.substring(0, 400) || 'fix applied')

  phase('Synthesize')

  const verify = await agent(
    `Run final verification:
1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid (if shared was changed)
3. ./gradlew :app:testDebugUnitTest
4. Boundary checks: null values, zero values, empty lists, edge cases

Report: PASS/FAIL for each.`,
    { label: 'final-verify', phase: 'Synthesize', effort: 'low' }
  )

  log(verify?.substring(0, 500) || 'verification complete')

  const allPassed = verify && !verify.toLowerCase().includes('fail')
  return {
    fixed: allPassed ? 1 : 0,
    total: 1,
    results: [{ id: 1, description: bug.description, diagnosis, fix, verify, passed: allPassed }]
  }
}

// ── Multi-bug: diagnose all in parallel (read-only) → fix sequentially ──
// Fixes are SERIAL on purpose: concurrent agents editing the same working tree
// and running Gradle at the same time contend on Gradle's project lock (deadlock)
// and spawn one 2GB daemon each. Only one Gradle process per working tree.

phase('Diagnose')

const diagnoses = await parallel(
  bugs.map(bug => () => agent(
    `Diagnose this bug: "${bug.description}"

READ-ONLY analysis: use grep/read only. Do NOT edit files. Do NOT run Gradle.

Check ALL layers:
1. Data layer: null safety, cumulative readings, Room/DataStore
2. Shared/KMP: java.time violations, algorithm errors
3. UI layer: state collection, colors, fonts, lifecycle
4. DI/infra: Hilt setup, build issues

Output:
- ROOT CAUSE: file:line — what's wrong
- FIX LOCATION: which file(s) need changes
- APPROACH: what specific change to make`,
    { label: `diag-bug-${bug.id}`, phase: 'Diagnose' }
  ))
)

phase('Fix')

const results = []
for (let i = 0; i < bugs.length; i++) {
  const bug = bugs[i]
  const diagnosis = diagnoses[i]
  if (!diagnosis) { results.push(null); continue }
  const fix = await agent(
    `Apply the MINIMAL fix for this diagnosed bug:

BUG: ${bug.description}
DIAGNOSIS: ${diagnosis}

${RULES}

After fixing, run: ./gradlew :app:compileDebugKotlin
Then run the most relevant test for the changed area.
Output: FIXED: file:line — what was changed, COMPILE: PASS/FAIL, TEST: PASS/FAIL`,
    { label: `fix-bug-${bug.id}`, phase: 'Fix', agentType: 'bug-fixer' }
  )
  results.push({ bug, diagnosis, fix, quickVerify: fix })
  log(`Bug #${bug.id}: ${fix ? 'fix applied' : 'fix agent returned nothing'}`)
}

phase('Synthesize')

const validResults = results.filter(Boolean)
const fixResults = validResults.filter(r => r.fix && !(r.fix.toLowerCase().includes('fail')))

log(`${fixResults.length}/${bugs.length} bugs fixed successfully`)

// Run full test suite once
const fullTest = await agent(
  `Run the full test suite to confirm all fixes work together:
1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid
3. ./gradlew :app:testDebugUnitTest

Report: PASS/FAIL with any failure details.`,
  { label: 'full-suite', phase: 'Synthesize', effort: 'low' }
)

const allPassed = fullTest && !fullTest.toLowerCase().includes('fail')
log(fullTest?.substring(0, 400) || 'full test complete')
log(allPassed ? '✅ All fixes verified!' : '⚠️ Some tests failed — review output above.')

const boundaryScan = await agent(
  `Run quick boundary checks on fixed code:
1. grep -rn "java\\.time\\|android\\." shared/src/commonMain/ --include="*.kt" → should be 0
2. grep -rn "Color(0x" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v "Color.kt" → check for new hardcoded colors
3. grep -rn "collectAsState()" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" → check for state collection issues
4. grep -rn "!!" app/src/main/java/com/example/energyflow/ --include="*.kt" → check for new non-null assertions

Report scan results.`,
  { label: 'boundary-scan', phase: 'Synthesize', effort: 'low' }
)

log(boundaryScan?.substring(0, 400) || 'boundary scan complete')

return {
  fixed: fixResults.length,
  total: bugs.length,
  fullTestPassed: allPassed,
  results: validResults.map(r => ({
    id: r.bug.id,
    description: r.bug.description,
    diagnosis: r.diagnosis?.substring(0, 200),
    fix: r.fix?.substring(0, 200),
    quickVerify: r.quickVerify?.substring(0, 200)
  })),
  fullTest,
  boundaryScan
}
