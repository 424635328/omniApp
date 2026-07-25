export const meta = {
  name: 'multi-feature',
  description: 'Parallel multi-feature development: analyze all in parallel → implement via pipeline → verify all',
  phases: [
    { title: 'Parse', detail: 'Extract feature list from args' },
    { title: 'Analyze', detail: 'All features analyzed in parallel for domain context' },
    { title: 'Implement', detail: 'Pipeline: each feature analyzed→implemented→compiled, no barrier between features' },
    { title: 'Verify', detail: 'Full test suite once for all features' }
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

// Accept features as array or semicolon-separated string
let features = []
if (Array.isArray(args?.features)) {
  features = args.features.map((f, i) => typeof f === 'string' ? { id: i + 1, description: f } : f)
} else if (typeof args?.features === 'string') {
  features = args.features.split(';').filter(Boolean).map((f, i) => ({ id: i + 1, description: f.trim() }))
} else if (typeof args?.feature === 'string') {
  features = [{ id: 1, description: args.feature }]
} else if (typeof args === 'string') {
  features = args.split(';').filter(Boolean).map((f, i) => ({ id: i + 1, description: f.trim() }))
} else {
  features = [{ id: 1, description: args?.description || 'the pending feature' }]
}

if (features.length === 0) {
  log('No features specified. Pass features as: args.features = ["feat1", "feat2"] or args.features = "feat1; feat2"')
  return { implemented: 0, results: [] }
}

log(`Processing ${features.length} feature(s): ${features.map(f => `#${f.id}: ${f.description.substring(0, 60)}`).join(', ')}`)

// ── Single feature fast path ──
if (features.length === 1) {
  const feature = features[0]

  phase('Analyze')

  const DOC_SETS = [
    {
      key: 'core',
      prompt: `Read and summarize for feature "${feature.description}":
1. .claude/docs/architecture/overview.md — project structure
2. .claude/docs/architecture/gotchas.md — known pitfalls
Output: relevant architecture constraints, gotchas, and rules.`
    },
    {
      key: 'domain',
      prompt: `Read and summarize for feature "${feature.description}" — READ ONLY the docs relevant to this feature:
- If billing/pricing: .claude/docs/data-layer/cost-engine.md
- If data parsing: .claude/docs/data-layer/smart-input-parser.md
- If anomaly detection: .claude/docs/data-layer/anomaly-detector.md
- If prediction: .claude/docs/analytics/predictive-analyzer.md
- If carbon/insights: .claude/docs/analytics/carbon-and-insight.md
- If KMP shared: .claude/docs/shared-kmp/module-design.md
- If UI/charts: .claude/docs/ui-layer/theme-and-navigation.md + chart-screen.md
- If settings/reports: .claude/docs/ui-layer/settings-and-reports.md
- If external API: .claude/docs/data-layer/external-services.md
Output: domain-specific knowledge, algorithms, data models, and constraints.`
    }
  ]

  const docsParallel = await parallel(
    DOC_SETS.map(d => () => agent(d.prompt, { label: `read:${d.key}`, phase: 'Analyze' }))
  )

  const core = docsParallel[0] || ''
  const domain = docsParallel[1] || ''

  log(`Docs loaded for: ${feature.description}`)

  phase('Implement')

  const impl = await agent(
    `Based on this analysis, implement the feature: "${feature.description}"

## Core Rules
${core?.substring(0, 800)}

## Domain Knowledge
${domain?.substring(0, 800)}

${RULES}

After editing, run: ./gradlew :app:compileDebugKotlin
If it fails, fix the errors before reporting done.`,
    { label: 'implement', phase: 'Implement', agentType: 'bug-fixer' }
  )

  log(impl?.substring(0, 400) || 'implementation complete')

  phase('Verify')

  const verify = await agent(
    `Run final verification:
1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid (if shared was changed)
3. ./gradlew :app:testDebugUnitTest
4. Boundary checks: grep for java.time in shared, Color(0x in ui, collectAsState() issues

Report: PASS/FAIL for each.`,
    { label: 'verify', phase: 'Verify' }
  )

  const allPassed = verify && !verify.toLowerCase().includes('fail')
  log(verify?.substring(0, 400) || 'verification complete')

  return {
    implemented: allPassed ? 1 : 0,
    total: 1,
    results: [{ id: 1, description: feature.description, impl, verify, passed: allPassed }]
  }
}

// ── Multi-feature pipeline: analyze → implement → compile-check, no barrier ──

phase('Analyze')

const featureContexts = await parallel(
  features.map(f => () => agent(
    `For feature: "${f.description}"
Quick-scout:
1. Which module(s) will this touch? (shared/data/ui/di)
2. Which doc files are relevant? (list specific paths)
3. What existing code will need changes?
4. Any dependencies between this and other features?
Output: brief scouting report (3-5 bullet points).`,
    { label: `scout-f${f.id}`, phase: 'Analyze' }
  ))
)

log(`Scouted ${features.length} features`)

phase('Implement')

const results = await pipeline(
  features.map((f, i) => ({ ...f, context: featureContexts[i] || '' })),
  // Stage 1: Implement
  async (item) => {
    const impl = await agent(
      `IMPLEMENT this feature: "${item.description}"

## Scout Report
${item.context?.substring(0, 500) || 'No scout data'}

${RULES}

After editing, run: ./gradlew :app:compileDebugKotlin
If FAIL, fix before reporting done.`,
      { label: `impl-f${item.id}`, phase: 'Implement', agentType: 'bug-fixer' }
    )
    return { feature: item, implementation: impl }
  },

  // Stage 2: Quick compile verify
  async (result) => {
    if (!result || !result.implementation) return null
    const compileCheck = await agent(
      `Run: ./gradlew :app:compileDebugKotlin
Report: PASS/FAIL. If FAIL, show the error.`,
      { label: `compile-f${result.feature.id}`, phase: 'Implement' }
    )
    const passed = compileCheck && !compileCheck.toLowerCase().includes('fail')
    if (!passed) {
      log(`⚠️ Feature #${result.feature.id} compile FAILED — attempting fix`)
      const fix = await agent(
        `Compile failed for feature: "${result.feature.description}"
Errors: ${compileCheck}

${RULES}
Fix the compile errors (minimum changes) and run ./gradlew :app:compileDebugKotlin again.`,
        { label: `fix-compile-f${result.feature.id}`, phase: 'Implement', agentType: 'bug-fixer' }
      )
      return { ...result, compileCheck, compileFixed: fix, compilePassed: fix && !fix.toLowerCase().includes('fail') }
    }
    return { ...result, compileCheck, compilePassed: true }
  }
)

phase('Verify')

const validResults = results.filter(Boolean)
const passedResults = validResults.filter(r => r.compilePassed)

log(`${passedResults.length}/${features.length} features compiled successfully`)

const fullTest = await agent(
  `Run the full test suite to confirm all features work together:
1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid
3. ./gradlew :app:testDebugUnitTest

Report: PASS/FAIL with any failure details.`,
  { label: 'full-suite', phase: 'Verify' }
)

const allPassed = fullTest && !fullTest.toLowerCase().includes('fail')
log(fullTest?.substring(0, 400) || 'full test complete')

const boundaryScan = await parallel([
  () => agent(
    `Run: grep -rn "java\\.time\\|android\\." shared/src/commonMain/ --include="*.kt"
Should be 0 results. Report findings.`,
    { label: 'check-kmp', phase: 'Verify' }
  ),
  () => agent(
    `Run: grep -rn "Color(0x" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v "Color.kt"
Check for new hardcoded colors. Report findings.`,
    { label: 'check-colors', phase: 'Verify' }
  ),
  () => agent(
    `Run: grep -rn "collectAsState()" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v ChartScreen
Check for state collection issues. Report findings.`,
    { label: 'check-state', phase: 'Verify' }
  ),
  () => agent(
    `Run: grep -rn "!!" app/src/main/java/com/example/energyflow/ --include="*.kt"
Check for new non-null assertions. Report findings.`,
    { label: 'check-null', phase: 'Verify' }
  )
])

log(allPassed ? '✅ All features verified!' : '⚠️ Some checks failed — review output above.')

return {
  implemented: passedResults.length,
  total: features.length,
  fullTestPassed: allPassed,
  results: validResults.map(r => ({
    id: r.feature.id,
    description: r.feature.description,
    implementation: r.implementation?.substring(0, 200),
    compilePassed: r.compilePassed
  })),
  fullTest,
  boundaryScan: boundaryScan.filter(Boolean).join('\n')
}
