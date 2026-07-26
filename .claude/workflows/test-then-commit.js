export const meta = {
  name: 'test-then-commit',
  description: 'Parallel scan + test → if all pass, generate commit message',
  phases: [
    { title: 'Scan+Test', detail: 'Scan what changed AND run tests in parallel (no dependency)' },
    { title: 'Commit', detail: 'Generate conventional commit message' }
  ]
}

// ── Phase 1: Scan and Test run IN PARALLEL ──

phase('Scan+Test')

const [diff, testResult] = await parallel([
  // Track 1: Scan what changed
  () => agent(
    `Run: git diff --stat and git diff --name-only

Then categorize each changed file:
- data layer: app/src/main/java/.../data/
- UI layer: app/src/main/java/.../ui/
- shared KMP: shared/src/
- DI: app/src/main/java/.../di/
- test: app/src/test/
- build config: *.gradle.kts
- docs: *.md

Also determine:
- Is this a feat (new capability), fix (bug fix), refactor (structure only), test (tests only), docs, or chore?
- Which test files are relevant to run first?`,
    { label: 'scan-changes',
      schema: {
      type: 'object',
      properties: {
        changedFiles: { type: 'array', items: { type: 'string' } },
        categories: { type: 'object', additionalProperties: { type: 'number' } },
        type: { type: 'string', enum: ['feat', 'fix', 'refactor', 'test', 'docs', 'chore', 'style', 'perf'] },
        primaryScope: { type: 'string', enum: ['data', 'ui', 'shared', 'di', 'widget', 'test', 'build', ''] },
        suggestedTests: { type: 'array', items: { type: 'string' } },
        description: { type: 'string' }
      },
      required: ['changedFiles', 'type', 'description']
    }}
  ),

  // Track 2: Run full test suite
  () => agent(
    `Run the following in order, stopping if any fail:

1. Quick compile check:
   ./gradlew :app:compileDebugKotlin

2. Shared module check:
   ./gradlew :shared:compileDebugKotlinAndroid 2>/dev/null || echo "# shared check skipped"

3. Full test suite:
   ./gradlew :app:testDebugUnitTest

Report: PASS/FAIL for each step. If any FAIL, show the error output.`,
    { label: 'run-tests', effort: 'low' }
  )
])

log(`Detected: ${diff?.type || '?'}(${diff?.primaryScope || 'global'}): ${diff?.description || '?'}`)
log(testResult?.substring(0, 400) || 'test results')

const allPassed = testResult && !testResult.toLowerCase().includes('fail')
if (!allPassed) {
  log('⚠️ Tests failed. Fix issues before committing.')
  return { passed: false, diff, testResult }
}

phase('Commit')

const commit = await agent(
  `All tests passed. Generate a conventional commit message.

Changes:
${diff?.changedFiles?.map(f => `  - ${f}`).join('\n') || 'unknown'}

Type: ${diff?.type || 'feat'}
Primary scope: ${diff?.primaryScope || 'global'}
Description: ${diff?.description || 'pending changes'}

Generate:
1. The commit message in format: <type>(<scope>): <description>
2. Optional body if warranted (for complex changes, explain why)
3. The exact git commands to execute

Output the commit message and commands. DO NOT execute git commit — the user will do it.`,
  { label: 'generate-commit' }
)

log(commit)

return {
  passed: true,
  diff,
  testResult,
  commitMessage: commit
}
