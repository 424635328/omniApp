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

  // Parallel multi-layer diagnosis for a single complex bug
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
      `Read .claude/docs/agents/quick-ref.md first.
${l.prompt}
Output: root cause hypothesis with file:line reference, or "NOT IN THIS LAYER" if the bug isn't here.`,
      { label: `diag:${l.key}`, phase: 'Diagnose' }
    ))
  )

  const relevantDiags = layerDiags.filter(Boolean).filter(d => !d.includes('NOT IN THIS LAYER'))
  log(`Layer diagnosis: ${relevantDiags.length}/${LAYERS.length} layers have relevant findings`)

  // Synthesize diagnoses
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
    `Read .claude/docs/agents/quick-ref.md.
Apply the MINIMAL fix for this diagnosed bug:

DIAGNOSIS:
${diagnosis}

RULES:
- Fix ONLY the bug, nothing else. Don't refactor adjacent code.
- Match existing code style exactly.
- After fixing, run: ./gradlew :app:compileDebugKotlin
- Then run any related tests.

After fixing, output:
- FIXED: file:line — what was changed
- COMPILE: PASS/FAIL`,
    { label: 'apply-fix', phase: 'Fix', agentType: 'bug-fixer' }
  )

  log(fix?.substring(0, 400) || 'fix applied')

  phase('Synthesize')

  // Full verification
  const verify = await agent(
    `Run final verification:
1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid (if shared was changed)
3. ./gradlew :app:testDebugUnitTest
4. Boundary checks: null values, zero values, empty lists, edge cases

Report: PASS/FAIL for each.`,
    { label: 'final-verify', phase: 'Synthesize' }
  )

  log(verify?.substring(0, 500) || 'verification complete')

  const allPassed = verify && !verify.toLowerCase().includes('fail')
  return {
    fixed: allPassed ? 1 : 0,
    total: 1,
    results: [{ id: 1, description: bug.description, diagnosis, fix, verify, passed: allPassed }]
  }
}

// ── Multi-bug pipeline: diagnose → fix → verify, no barrier between bugs ──

phase('Diagnose')

// Stage 1: Diagnose each bug (runs in parallel across bugs)
// Stage 2: Fix each diagnosed bug (pipeline — bug A fixes while bug B diagnoses)
// Stage 3: Verify each fixed bug

const results = await pipeline(
  bugs,
  // Stage 1: Diagnose
  async (bug) => {
    const diag = await agent(
      `Read .claude/docs/agents/quick-ref.md.
Diagnose this bug: "${bug.description}"

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
    )
    return { bug, diagnosis: diag }
  },

  // Stage 2: Fix (each bug fixes independently as soon as diagnosed)
  async (result) => {
    if (!result || !result.diagnosis) return null
    const fix = await agent(
      `Read .claude/docs/agents/quick-ref.md.
Apply the MINIMAL fix for this diagnosed bug:

BUG: ${result.bug.description}
DIAGNOSIS: ${result.diagnosis}

RULES:
- Fix ONLY this bug, nothing else
- Match existing code style exactly
- After fixing, run: ./gradlew :app:compileDebugKotlin

Output: FIXED: file:line — what was changed, and COMPILE: PASS/FAIL`,
      { label: `fix-bug-${result.bug.id}`, phase: 'Fix', agentType: 'bug-fixer' }
    )
    return { ...result, fix }
  },

  // Stage 3: Quick verify (compile + related test)
  async (result) => {
    if (!result || !result.fix) return null
    const testVerify = await agent(
      `Quick verify fix for bug #${result.bug.id}: "${result.bug.description}"
1. ./gradlew :app:compileDebugKotlin
2. Run the most relevant test for the changed area
Report: PASS/FAIL`,
      { label: `verify-bug-${result.bug.id}`, phase: 'Fix' }
    )
    return { ...result, quickVerify: testVerify }
  }
)

phase('Synthesize')

// Final full test suite (run once for all fixes)
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
  { label: 'full-suite', phase: 'Synthesize' }
)

const allPassed = fullTest && !fullTest.toLowerCase().includes('fail')
log(fullTest?.substring(0, 400) || 'full test complete')
log(allPassed ? '✅ All fixes verified!' : '⚠️ Some tests failed — review output above.')

// Boundary scan
const boundaryScan = await agent(
  `Run quick boundary checks on fixed code:
1. grep -rn "java\.time\|android\." shared/src/commonMain/ --include="*.kt"  → should be 0
2. grep -rn "Color(0x" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v "Color.kt" → check for new hardcoded colors
3. grep -rn "collectAsState()" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v ChartScreen → check for state collection issues
4. grep -rn "!!" app/src/main/java/com/example/energyflow/ --include="*.kt" → check for new non-null assertions

Report scan results.`,
  { label: 'boundary-scan', phase: 'Synthesize' }
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
