export const meta = {
  name: 'feature-development',
  description: 'Full feature cycle: understand domain → design plan → implement → test → verify',
  phases: [
    { title: 'Understand', detail: 'Read relevant docs and existing tests' },
    { title: 'Design', detail: 'Produce implementation plan' },
    { title: 'Implement', detail: 'Write code with compile+test checks' },
    { title: 'Verify', detail: 'Run full test suite and verify behavior' }
  ]
}

phase('Understand')

// Step 1: Gather domain knowledge
const domain = await agent(
  `Read these docs and summarize the relevant parts for the feature: "${args.feature || 'the pending changes'}":
1. .claude/docs/architecture/overview.md
2. .claude/docs/architecture/gotchas.md (especially relevant pitfalls)
3. Any domain docs relevant to the feature (cost-engine, predictive-analyzer, smart-input-parser, etc.)
4. The existing tests for the area being changed

Output:
- Which files will likely be touched
- Which gotchas are relevant
- Which tests serve as the behavior contract
- Any ADR constraints that apply`,
  { label: 'domain-analysis' }
)

log(`Domain analysis: ${domain?.substring(0, 200)}...`)

phase('Design')

// Step 2: Create implementation plan
const plan = await agent(
  `Based on this domain analysis, create a step-by-step implementation plan:
${domain}

For each step, specify:
1. What file(s) to create/modify
2. What the change is (concrete, not abstract)
3. What test to write FIRST
4. What to verify after the step

Rules:
- KMP shared module: no java.time, no android.*
- Compose: MonoFontFamily, theme colors only (ElectricColor etc.), collectAsStateWithLifecycle()
- Hilt: @Singleton for engines, @HiltViewModel for VMs
- No speculative abstractions — minimum code that solves the problem
- Each step should be independently verifiable

Format as a numbered checklist.`,
  { label: 'design-plan' }
)

log(plan)

phase('Implement')

// Step 3: Implement following the plan
const implementation = await agent(
  `Implement the feature following this plan exactly:

${plan}

Critical rules — violate any of these and the code is WRONG:
1. KMP: shared/src/commonMain/ MUST NOT import java.time or android.* — use kotlinx.datetime
2. Compose: ALL colors must use theme colors (ElectricColor, DarkBackground, etc.) — NO hardcoded hex
3. Font: use MonoFontFamily everywhere
4. State: use collectAsStateWithLifecycle(), not collectAsState()
5. Hilt: engines are @Singleton class X @Inject constructor(deps)
6. Minimal: no abstractions for single use, no "just in case" code
7. Surgical: don't modify adjacent code that isn't related to this feature

After each file edit, run: ./gradlew :app:compileDebugKotlin
If it fails, fix the errors before continuing.

Write tests before or alongside the implementation code.`,
  { label: 'implement' }
)

log(implementation?.substring(0, 500) || 'implementation complete')

phase('Verify')

// Step 4: Run full validation
const verify = await agent(
  `Run these verification steps and report results:

1. ./gradlew :app:compileDebugKotlin
2. ./gradlew :shared:compileDebugKotlinAndroid (if shared was changed)
3. ./gradlew :app:testDebugUnitTest
4. Quick scan check:
   - grep for "java.time" in shared/src/commonMain/ (should be 0)
   - grep for "Color(0x" in new UI code (should use theme colors)
   - grep for "collectAsState()" in new UI code (should use collectAsStateWithLifecycle())
   - grep for "!!" on MeterRecord fields (should use safe access)

Report: PASS/FAIL for each check. If any FAIL, explain why and suggest fix.`,
  { label: 'verify' }
)

log(verify)

return {
  domain,
  plan,
  verification: verify
}
