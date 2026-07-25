export const meta = {
  name: 'bug-fix',
  description: 'Structured bug fix: reproduce → diagnose → fix → regression test → verify',
  phases: [
    { title: 'Reproduce', detail: 'Write failing regression test' },
    { title: 'Diagnose', detail: 'Identify root cause across layers' },
    { title: 'Fix', detail: 'Apply minimal fix and confirm test passes' },
    { title: 'Verify', detail: 'Full suite + boundary checks' }
  ]
}

const bug = args?.bug || args?.description || 'the reported bug'

phase('Reproduce')

// Step 1: Write a failing test that reproduces the bug
const repro = await agent(
  `Read .claude/docs/architecture/gotchas.md first (many bugs are known pitfalls).

Then, for this bug: "${bug}"

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

// Step 2: Root cause analysis
const diagnosis = await agent(
  `A failing test has been written for this bug: "${bug}"

Read .claude/docs/architecture/gotchas.md carefully.

Now diagnose the root cause. Check each layer:

1. Is it a KMP boundary issue? (java.time in shared?)
2. Is it a data layer issue? (null handling, cumulative vs delta, Room query?)
3. Is it a UI layer issue? (collectAsState vs collectAsStateWithLifecycle, hardcoded color?)
4. Is it a DI issue? (Hilt cache poisoning, missing @Inject?)
5. Is it an algorithm issue? (math error in CostEngine/PredictiveAnalyzer?)

Common EnergyFlow-specific bugs:
- Hilt build cache → ClassNotFoundException → rerun-tasks
- Room destructive migration → data loss on schema change
- Null vs Zero confusion → null means "not recorded", 0.0 means "recorded zero"
- Cumulative readings → subtraction direction wrong (current - previous)
- SmartInputParser year assumption → uses Year.now(), can't parse historical years
- AdaptiveClassifier threshold drift → anomalous data skews thresholds
- Peak/Valley pairing state machine → incomplete pairs saved independently
- Forecast sentinel value → -1.0 marks projected points

Output:
1. Root cause (with file:line reference)
2. Why the existing code behaves this way
3. Which layer the fix should be in`,
  { label: 'diagnose-root-cause' }
)

log(diagnosis?.substring(0, 500) || 'diagnosis complete')

phase('Fix')

// Step 3: Apply minimal fix
const fix = await agent(
  `Apply the minimal fix for this diagnosed bug:

${diagnosis}

Rules:
- MINIMUM change — fix only the bug, nothing else
- Don't refactor adjacent code
- Don't add features or "improvements"
- Match existing code style exactly
- After the fix, run the reproduction test: ./gradlew :app:testDebugUnitTest --tests "<test name>"
  Expected: PASS (bug is fixed)

If the test still fails, re-examine the diagnosis.`,
  { label: 'apply-fix' }
)

log(fix?.substring(0, 300) || 'fix applied')

phase('Verify')

// Step 4: Full verification
const verify = await agent(
  `Verify the bug fix:

1. Run the reproduction test:
   ./gradlew :app:testDebugUnitTest --tests "<reproduction test>"
   Expected: PASS

2. Run the full test suite:
   ./gradlew :app:testDebugUnitTest
   Expected: all existing tests still PASS

3. Compile check:
   ./gradlew :app:compileDebugKotlin
   ./gradlew :shared:compileDebugKotlinAndroid (if shared changed)

4. Boundary check — think about edge cases that could still fail:
   - Null values?
   - Zero values?
   - Empty lists?
   - Extreme values?
   - Cross-month/cross-year boundaries?

Report: PASS/FAIL for each check. If all PASS, the fix is verified.`,
  { label: 'full-verify' }
)

log(verify)

return {
  reproduction: repro,
  diagnosis,
  verification: verify
}
