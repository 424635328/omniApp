export const meta = {
  name: 'feature-development',
  description: 'Full feature cycle: understand domain → design plan → implement (parallel where possible) → verify',
  phases: [
    { title: 'Understand', detail: 'Read relevant docs and existing tests' },
    { title: 'Design', detail: 'Produce implementation plan with parallelizable steps' },
    { title: 'Implement', detail: 'Execute steps — independent steps run in parallel' },
    { title: 'Verify', detail: 'Run full test suite and boundary checks' }
  ]
}

const feature = args?.feature || args?.description || 'the pending changes'

phase('Understand')

// Step 1: Gather domain knowledge (parallel doc reading)
const DOC_SETS = [
  {
    key: 'core',
    prompt: `Read and summarize for feature "${feature}":
1. .claude/docs/architecture/overview.md — project structure
2. .claude/docs/architecture/gotchas.md — known pitfalls
3. .claude/docs/agents/quick-ref.md — compressed rules
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

const docsParallel = await parallel(
  DOC_SETS.map(d => () => agent(d.prompt, { label: `read:${d.key}`, phase: 'Understand' }))
)

const core = docsParallel[0] || ''
const domain = docsParallel[1] || ''
const tests = docsParallel[2] || ''

log(`Docs loaded: core rules + domain knowledge + existing tests`)

phase('Design')

// Step 2: Create implementation plan with parallelizable steps marked
const plan = await agent(
  `Based on this analysis, create a step-by-step implementation plan:

## Core Rules
${core?.substring(0, 1000)}

## Domain Knowledge
${domain?.substring(0, 1000)}

## Existing Tests
${tests?.substring(0, 500)}

FEATURE: "${feature}"

For each step, specify:
1. What file(s) to create/modify
2. What the change is (concrete, not abstract)
3. Whether this step CAN RUN IN PARALLEL with other steps (if it touches independent files)
4. What test to write FIRST
5. What to verify after the step

Mark parallelizable steps with [PARALLEL] prefix. Steps that depend on previous steps get [SEQ].

Rules:
- KMP shared: no java.time, no android.* → kotlinx.datetime
- Compose: MonoFontFamily, theme colors only, collectAsStateWithLifecycle()
- Hilt: @Singleton for engines, @HiltViewModel for VMs
- No speculative abstractions — minimum code
- Each step independently verifiable

Format as numbered checklist with [PARALLEL]/[SEQ] markers.`,
  { label: 'design-plan' }
)

log(plan?.substring(0, 500) || 'plan created')

phase('Implement')

// Step 3: Implement — parallelize independent steps
// Parse the plan to extract parallel vs sequential groups
const planAnalysis = await agent(
  `Analyze this implementation plan and extract the execution order:

${plan}

Output a JSON array of phases. Each phase has:
- "steps": array of step descriptions (these run in PARALLEL within the phase)
- "dependsOn": which previous phase index this depends on (null for first phase)

Steps that can run in parallel (marked [PARALLEL] or touch independent files) go in the SAME phase.
Steps that depend on previous steps (marked [SEQ]) each get their OWN phase.

Example:
[
  {"steps": ["Create data class X", "Create data class Y"], "dependsOn": null},
  {"steps": ["Implement engine using X and Y"], "dependsOn": 0}
]`,
  { label: 'parallelize-plan',
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
  const phase = planAnalysis.phases[i]
  log(`Phase ${i + 1}: ${phase.steps.length} step(s) ${phase.steps.length > 1 ? '(PARALLEL)' : ''}`)

  await parallel(
    phase.steps.map((step, j) => () => agent(
      `Read .claude/docs/agents/quick-ref.md.

IMPLEMENT this step for feature "${feature}":

${step}

CRITICAL RULES:
1. KMP: shared/src/commonMain/ MUST NOT import java.time or android.*
2. Compose: ALL colors → theme colors (ElectricColor, etc.) — NO Color(0xFF...)
3. Font: MonoFontFamily everywhere
4. State: collectAsStateWithLifecycle(), not collectAsState()
5. Hilt: @Singleton class X @Inject constructor(deps)
6. Minimal: no abstractions for single use
7. Surgical: don't modify unrelated adjacent code

After editing, run: ./gradlew :app:compileDebugKotlin
If it fails, fix the errors before reporting done.`,
      { label: `impl-${i + 1}-${j + 1}`, phase: 'Implement' }
    ))
  )

  // Compile check after each phase
  const compileCheck = await agent(
    `Run: ./gradlew :app:compileDebugKotlin
Report: PASS/FAIL. If FAIL, show the error and suggest fix.`,
    { label: `compile-p${i + 1}` }
  )

  if (compileCheck && compileCheck.toLowerCase().includes('fail')) {
    log(`⚠️ Compile failed after phase ${i + 1}. Fixing before continuing.`)
    // Fix compile errors
    await agent(
      `Compile failed after implementing these steps:
${phase.steps.join('\n')}

Errors:
${compileCheck}

Fix the compile errors (minimum changes). Then run ./gradlew :app:compileDebugKotlin again.`,
      { label: `fix-compile-p${i + 1}` }
    )
  }
}

log('Implementation complete')

phase('Verify')

// Step 4: Run full validation
const verify = await agent(
  `Run these verification steps and report results:

1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid (if shared was changed)
3. ./gradlew :app:testDebugUnitTest
4. Quick scan:
   - grep -rn "java\.time\|android\." shared/src/commonMain/ --include="*.kt" → should be 0
   - grep -rn "Color(0x" app/src/main/java/com/example/energyflow/ui/ --include="*.kt" | grep -v "Color.kt"
   - grep -rn "collectAsState()" <new ui files> | grep -v ChartScreen
   - grep -rn "!!" <new code> — check for unsafe null assertions

Report: PASS/FAIL for each check. If any FAIL, explain why and suggest fix.`,
  { label: 'verify' }
)

log(verify?.substring(0, 500) || 'verification complete')

return {
  core,
  domain,
  plan,
  verification: verify
}
