export const meta = {
  name: 'full-review',
  description: '4-dimension parallel review: code correctness, architecture, analytics math, UI quality',
  phases: [
    { title: 'Review', detail: '4 agents review in parallel across dimensions' },
    { title: 'Synthesize', detail: 'Merge and deduplicate findings' }
  ]
}

// Step 0: Get the changed files to scope the review
const changedFiles = await agent(
  `Run: git diff main...HEAD --name-only
Then output the list of changed files, categorized by module:
- shared/ files
- app/data/ files
- app/ui/ files
- app/di/ files
- test files
- build/config files`,
  { label: 'scope-changes',
    schema: {
      type: 'object',
      properties: {
        shared: { type: 'array', items: { type: 'string' } },
        data: { type: 'array', items: { type: 'string' } },
        ui: { type: 'array', items: { type: 'string' } },
        di: { type: 'array', items: { type: 'string' } },
        test: { type: 'array', items: { type: 'string' } },
        build: { type: 'array', items: { type: 'string' } }
      }
    }
  }
)

const allChanged = [
  ...(changedFiles?.shared || []),
  ...(changedFiles?.data || []),
  ...(changedFiles?.ui || []),
  ...(changedFiles?.di || [])
]

const hasShared = (changedFiles?.shared || []).length > 0
const hasData = (changedFiles?.data || []).length > 0
const hasUI = (changedFiles?.ui || []).length > 0
const hasDI = (changedFiles?.di || []).length > 0
const totalChanged = allChanged.length

log(`Scoped review to ${totalChanged} changed files (shared:${changedFiles?.shared?.length || 0} data:${changedFiles?.data?.length || 0} ui:${changedFiles?.ui?.length || 0} di:${changedFiles?.di?.length || 0})`)

if (totalChanged === 0) {
  log('No changed files to review. If this is unexpected, check the base branch.')
  return { findings: [], byDimension: { code: 0, architecture: 0, analytics: 0, ui: 0 } }
}

const scopeContext = `Changed files to review:
${allChanged.map(f => `  - ${f}`).join('\n')}`

// Four review dimensions, each handled by a specialized agent
const DIMENSIONS = [
  {
    key: 'code',
    agentType: 'code-reviewer',
    prompt: `${scopeContext}

Read .claude/docs/agents/quick-ref.md first.

Review ONLY these changed files for:
- Correctness bugs: null safety violations, edge cases, logic errors
- Style violations: hardcoded hex colors (should use theme colors like ElectricColor), FontFamily != MonoFontFamily, collectAsState() instead of collectAsStateWithLifecycle()
- KMP boundary violations: java.time or android.* in shared/src/commonMain/
- Room safety: destructive migration implications, null field handling
Report every finding with file:line, severity (critical/warning/info), and a one-sentence description.`
  },
  {
    key: 'architecture',
    agentType: 'architecture-reviewer',
    prompt: `${scopeContext}

Review the current diff for architectural issues:
- Module boundary violations (should shared logic be in shared/? should Android-only code stay in app/?)
- DI pattern consistency (Hilt wrapper pattern, @Singleton vs @HiltViewModel)
- Data flow correctness (does data flow through the right layers?)
- New dependencies that could be avoided
- Design decisions that contradict existing ADRs (an0301-tab-navigation, an0302-hilt-wrapper, an0303-adaptive-classifier)
Report each finding with file:line and concrete explanation.`
  },
  {
    key: 'analytics',
    agentType: 'analytics-reviewer',
    prompt: `${scopeContext}

Review the current diff for analytics/math correctness in:
- CostEngine / CostEngineShared: tier calculations, peak/valley cost distribution, water pricing
- PredictiveAnalyzer / PredictiveAnalyzerShared: DES algorithm, weather multiplier, weekend factor
- AnomalyDetector / AnomalyDetectorShared: spike detection threshold (5x), monotonic decrease check
- CarbonCalculator: emission factors, badge logic
- EventImpactAnalyzer: window detection, daily rate comparison
- WeatherInterpolator: linear interpolation bounds
Report each finding with file:line and the specific math error.`
  },
  {
    key: 'ui',
    agentType: 'ui-reviewer',
    prompt: `${scopeContext}

Review the current diff for UI issues in Compose code:
- Performance: missing remember{}, unstable lambda params, unnecessary recomposition
- Accessibility: missing contentDescription, poor contrast
- Theme compliance: hardcoded colors, wrong font family
- Animation: jank-inducing patterns, missing animateItem()
- Design consistency: spacing, sizing, component reuse
Report each finding with file:line and severity.`
  }
]

phase('Review')

// Each dimension reviews independently — they start and finish at their own pace
const results = await pipeline(
  DIMENSIONS,
  d => agent(d.prompt, {
    label: `review:${d.key}`,
    agentType: d.agentType,
    schema: {
      type: 'object',
      properties: {
        findings: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              file: { type: 'string' },
              line: { type: 'number' },
              summary: { type: 'string' },
              severity: { type: 'string', enum: ['critical', 'warning', 'info'] }
            },
            required: ['file', 'summary', 'severity']
          }
        }
      },
      required: ['findings']
    }
  })
)

phase('Synthesize')

// Flatten and deduplicate by file+line
const allFindings = results.filter(Boolean).flatMap(r => r.findings)
const seen = new Set()
const unique = allFindings.filter(f => {
  const key = `${f.file}:${f.line}`
  if (seen.has(key)) return false
  seen.add(key)
  return true
})

// Sort by severity
const order = { critical: 0, warning: 1, info: 2 }
unique.sort((a, b) => (order[a.severity] || 3) - (order[b.severity] || 3))

log(`${unique.length} unique findings (${unique.filter(f => f.severity === 'critical').length} critical, ${unique.filter(f => f.severity === 'warning').length} warning, ${unique.filter(f => f.severity === 'info').length} info)`)

return {
  findings: unique,
  byDimension: {
    code: results[0]?.findings?.length || 0,
    architecture: results[1]?.findings?.length || 0,
    analytics: results[2]?.findings?.length || 0,
    ui: results[3]?.findings?.length || 0
  }
}
