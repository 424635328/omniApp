export const meta = {
  name: 'feature-development',
  description: 'Full feature cycle: parallel Understand → 3-panel Design → pipeline Implement → parallel Verify',
  phases: [
    { title: 'Understand', detail: 'Parallel doc reading for domain context' },
    { title: 'Design', detail: '3-agent panel: MVP-first, risk-first, user-first → synthesize winner' },
    { title: 'Implement', detail: 'Execute steps — independent steps run in parallel within each phase' },
    { title: 'Verify', detail: 'Parallel compile + test + boundary scan' }
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

const feature = args?.feature || args?.description || 'the pending changes'

phase('Understand')

// Step 1: Gather domain knowledge (parallel doc reading)
const DOC_SETS = [
  {
    key: 'core',
    prompt: `Read and summarize for feature "${feature}":
1. .claude/docs/architecture/overview.md — project structure
2. .claude/docs/architecture/gotchas.md — known pitfalls
Output: relevant architecture constraints, gotchas, and rules.`
  },
  {
    key: 'domain',
    prompt: `Read and summarize for feature "${feature}" — READ ONLY the docs relevant to this feature:
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
  },
  {
    key: 'tests',
    prompt: `Find and summarize existing tests relevant to "${feature}":
Look in app/src/test/java/com/example/energyflow/ for test files related to the feature area.
Output: which test files exist, what they cover, and what the behavior contracts are.`
  }
]

// Doc summarization is mechanical — low effort keeps this phase fast and cheap
const docsParallel = await parallel(
  DOC_SETS.map(d => () => agent(d.prompt, { label: `read:${d.key}`, phase: 'Understand', effort: 'low' }))
)

const core = docsParallel[0] || ''
const domain = docsParallel[1] || ''
const tests = docsParallel[2] || ''

log(`Docs loaded: core rules + domain knowledge + existing tests`)

phase('Design')

// Step 2: PARALLEL DESIGN PANEL — 3 agents produce independent plans from different angles
const CONTEXT = `FEATURE: "${feature}"

## Core Rules
${core?.substring(0, 800)}

## Domain Knowledge
${domain?.substring(0, 800)}

## Existing Tests
${tests?.substring(0, 400)}

${RULES}`

const DESIGN_ANGLES = [
  {
    key: 'mvp',
    prompt: `${CONTEXT}

Create an MVP-first implementation plan.
Focus: absolute minimum code to make the feature work. Ship fast.
For each step: what file + what change + how to verify.
Mark parallelizable steps with [PARALLEL], dependent steps with [SEQ].`
  },
  {
    key: 'risk',
    prompt: `${CONTEXT}

Create a risk-first implementation plan.
Focus: identify what could break, plan tests and safeguards first.
For each step: what file + what change + what risk it mitigates + how to verify.
Mark parallelizable steps with [PARALLEL], dependent steps with [SEQ].`
  },
  {
    key: 'user',
    prompt: `${CONTEXT}

Create a user-first implementation plan.
Focus: what delivers the most user value soonest, then polish.
For each step: what file + what change + what user sees + how to verify.
Mark parallelizable steps with [PARALLEL], dependent steps with [SEQ].`
  }
]

const designPanel = await parallel(
  DESIGN_ANGLES.map(d => () => agent(d.prompt, { label: `design:${d.key}`, phase: 'Design' }))
)

// Synthesize: judge picks the best plan, grafts ideas from runners-up
const synthesis = await agent(
  `You are the lead architect. Three independent designers produced plans for: "${feature}"

## MVP-First Plan
${designPanel[0]?.substring(0, 1500) || 'N/A'}

## Risk-First Plan
${designPanel[1]?.substring(0, 1500) || 'N/A'}

## User-First Plan
${designPanel[2]?.substring(0, 1500) || 'N/A'}

Your job:
1. Pick the WINNER — which plan is the best foundation? Explain why.
2. GRAFT the best ideas from the other two plans into the winner.
3. Output the FINAL SYNTHESIZED PLAN as numbered steps with [PARALLEL]/[SEQ] markers.

${RULES}
- Each step independently verifiable`,
  { label: 'synthesize-plan', phase: 'Design' }
)

log(synthesis?.substring(0, 500) || 'plan synthesized')

phase('Implement')

// Step 3: Parse the plan and execute phases with parallelism
const planAnalysis = await agent(
  `Analyze this implementation plan and extract the execution order:

${synthesis}

Output a JSON array of phases. Each phase has:
- "steps": array of step descriptions (these run in PARALLEL within the phase)
- "dependsOn": which previous phase index this depends on (null for first phase)

Steps that can run in parallel (marked [PARALLEL] or touch independent files) go in the SAME phase.
Steps that depend on previous steps (marked [SEQ]) each get their OWN phase.`,
  { label: 'parallelize-plan',
    effort: 'low',
    schema: {
      type: 'object',
      properties: {
        phases: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              steps: { type: 'array', items: { type: 'string' } },
              dependsOn: { type: 'number' }
            },
            required: ['steps']
          }
        }
      },
      required: ['phases']
    }
  }
)

// Execute phases — steps within each phase run in parallel
for (let i = 0; i < (planAnalysis?.phases?.length || 0); i++) {
  const phaseData = planAnalysis.phases[i]
  log(`Phase ${i + 1}/${planAnalysis.phases.length}: ${phaseData.steps.length} step(s) ${phaseData.steps.length > 1 ? '(PARALLEL)' : ''}`)

  // Parallel step agents EDIT ONLY — no Gradle. Concurrent Gradle invocations
  // in the same working tree deadlock on the project lock and spawn one 2GB
  // daemon each. The single compile-check agent below builds once per phase.
  await parallel(
    phaseData.steps.map((step, j) => () => agent(
      `IMPLEMENT this step for feature "${feature}":

${step}

${RULES}

EDIT ONLY: do NOT run Gradle — other implementation agents run concurrently and
concurrent Gradle builds deadlock. A separate compile step runs after this phase.`,
      { label: `impl-p${i + 1}-s${j + 1}`, phase: 'Implement', agentType: 'bug-fixer' }
    ))
  )

  // Single serialized compile check after every phase
  {
    const compileCheck = await agent(
      `Run: ./gradlew :app:compileDebugKotlin
Report: PASS/FAIL. If FAIL, show the error and suggest fix.`,
      { label: `compile-p${i + 1}`, phase: 'Implement', effort: 'low' }
    )

    if (compileCheck && compileCheck.toLowerCase().includes('fail')) {
      log(`⚠️ Compile failed after phase ${i + 1}. Auto-fixing.`)
      await agent(
        `Compile failed. Errors:
${compileCheck}

${RULES}
Fix the compile errors (MINIMUM changes). Run ./gradlew :app:compileDebugKotlin after fixing.`,
        { label: `fix-compile-p${i + 1}`, phase: 'Implement', agentType: 'bug-fixer' }
      )
    }
  }
}

log('Implementation complete')

phase('Verify')

// Step 4: verification — ONE agent owns all Gradle invocations (serial inside),
// while the grep-only boundary scan runs alongside it. Never run two Gradle
// builds concurrently in the same working tree.
const verifyResults = await parallel([
  () => agent(
    `Run these IN ORDER (never in parallel):
1. ./gradlew :app:testDebugUnitTest
2. ./gradlew :shared:compileDebugKotlinAndroid
Report: PASS/FAIL for each. If FAIL, show which tests failed and the error output.`,
    { label: 'gradle-verify', phase: 'Verify', effort: 'low' }
  ),
  () => agent(
    `Run all boundary checks in sequence:
1. KMP: grep -rn "java\\.time\\|android\\." shared/src/commonMain/ --include="*.kt" → should be 0
2. Colors: grep -rn "Color(0x" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v "Color.kt"
3. State: grep -rn "collectAsState()" app/src/main/java/com/example/energyflow/ui/ --include="*.kt"
4. Null: grep -rn "!!" app/src/main/java/com/example/energyflow/ --include="*.kt"

Report: PASS/FAIL for each check. If any FAIL, show the violations.`,
    { label: 'boundary-scan', phase: 'Verify', effort: 'low' }
  )
])

const testResult = verifyResults[0] || ''
const sharedCompile = testResult
const boundaryResult = verifyResults[1] || ''

const allPassed = testResult && !testResult.toLowerCase().includes('fail')

log(testResult?.substring(0, 300) || 'tests complete')
log(boundaryResult?.substring(0, 300) || 'boundary scan complete')
log(allPassed ? '✅ All checks passed!' : '⚠️ Some checks failed — see output above.')

return {
  core,
  domain,
  plan: synthesis,
  testResult,
  sharedCompile,
  boundaryResult,
  allPassed
}
