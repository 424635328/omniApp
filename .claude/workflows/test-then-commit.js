export const meta = {
  name: 'test-then-commit',
  description: 'Run tests → if all pass, generate commit message and offer to commit',
  phases: [
    { title: 'Scan', detail: 'Check what changed and which tests are affected' },
    { title: 'Test', detail: 'Run affected tests, then full suite' },
    { title: 'Commit', detail: 'Generate conventional commit message' }
  ]
}

phase('Scan')

// See what changed
const diff = await agent(
  `Read .claude/docs/agents/quick-ref.md first.
Run: git diff --stat and git diff --name-only

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
  { schema: {
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
)

log(`Detected: ${diff.type}(${diff.primaryScope || 'global'}): ${diff.description}`)

phase('Test')

// Run tests
const testResult = await agent(
  `Run the following in order, stopping if any fail:

1. Quick compile check:
   ./gradlew :app:compileDebugKotlin

2. Shared module check (if shared changed):
   ${diff.categories?.shared ? './gradlew :shared:compileDebugKotlinAndroid' : '# shared not changed, skip'}

3. Suggested tests first:
   ${diff.suggestedTests?.map(t => `./gradlew :app:testDebugUnitTest --tests "${t}"`).join('\n   ') || '# no specific tests suggested'}

4. Full test suite:
   ./gradlew :app:testDebugUnitTest

Report: PASS/FAIL for each step. If any FAIL, show the error output.`,
  { label: 'run-tests' }
)

log(testResult?.substring(0, 500) || 'test results')

// Check if all passed
const allPassed = testResult && !testResult.toLowerCase().includes('fail')
if (!allPassed) {
  log('⚠️ Tests failed. Fix issues before committing.')
  return { passed: false, testResult }
}

phase('Commit')

// Generate commit
const commit = await agent(
  `All tests passed. Generate a conventional commit message.

Changes:
${diff.changedFiles?.map(f => `  - ${f}`).join('\n')}

Type: ${diff.type}
Primary scope: ${diff.primaryScope || 'global'}
Description: ${diff.description}

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
