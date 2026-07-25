export const meta = {
  name: 'onboarding',
  description: 'Codebase orientation: parallel exploration → structured map → guided walkthrough',
  phases: [
    { title: 'Explore', detail: 'Parallel readers sweep the codebase' },
    { title: 'Map', detail: 'Synthesize a structured project map' },
    { title: 'Walkthrough', detail: 'Report the guided tour' }
  ]
}

phase('Explore')

// 4 parallel explorers, each reading a different subsystem
const explorers = await parallel([
  () => agent(
    `Read .claude/docs/agents/quick-ref.md first.
Read these files and summarize the architecture layer:
- .claude/docs/architecture/overview.md
- .claude/docs/architecture/adr-001-tab-navigation.md
- .claude/docs/architecture/adr-002-hilt-wrapper-pattern.md
- .claude/docs/architecture/adr-003-adaptive-classifier.md
- .claude/docs/architecture/app-entry-and-di.md
- .claude/docs/architecture/build-and-test.md

Output a structured summary of: module structure, design patterns, DI setup, entry points.`,
    { label: 'explore-architecture' }
  ),
  () => agent(
    `Read these files and summarize the data layer:
- .claude/docs/data-layer/meter-record.md
- .claude/docs/data-layer/cost-engine.md
- .claude/docs/data-layer/smart-input-parser.md
- .claude/docs/data-layer/anomaly-detector.md
- .claude/docs/data-layer/external-services.md

Output a structured summary of: data model, business engines, parsing pipeline, external integrations.`,
    { label: 'explore-data' }
  ),
  () => agent(
    `Read these files and summarize the UI layer:
- .claude/docs/ui-layer/theme-and-navigation.md
- .claude/docs/ui-layer/chart-screen.md
- .claude/docs/ui-layer/settings-and-reports.md

Output a structured summary of: navigation system, theme/color system, screen components, component tree.`,
    { label: 'explore-ui' }
  ),
  () => agent(
    `Read these files and summarize the testing + quality system:
- .claude/docs/testing/strategy.md
- .claude/docs/testing/test-cases.md
- .claude/docs/testing/process.md
- .claude/docs/testing/dev-workflow.md
- .claude/docs/architecture/gotchas.md

Output a structured summary of: test pyramid, test patterns, coverage gaps, known pitfalls, dev workflow.`,
    { label: 'explore-testing' }
  )
])

phase('Map')

// Synthesize into one coherent map
const projectMap = await agent(
  `Synthesize these 4 exploration summaries into ONE coherent project map:

## Architecture
${explorers[0]?.substring(0, 2000) || 'N/A'}

## Data Layer
${explorers[1]?.substring(0, 2000) || 'N/A'}

## UI Layer
${explorers[2]?.substring(0, 2000) || 'N/A'}

## Testing & Quality
${explorers[3]?.substring(0, 2000) || 'N/A'}

Create a structured project map with:
1. **Project Identity** — one paragraph: what it is, who it's for, key capabilities
2. **Data Flow** — simplified pipeline diagram (text-based)
3. **Module Index** — every major file with its role and dependencies
4. **Design Decisions** — the 3-5 most important architectural choices and WHY
5. **Gotchas Top 5** — most likely pitfalls for new contributors
6. **Getting Started** — the 3 commands and 3 files someone needs to make their first change

Keep it actionable — this is a reference someone will use while coding.`,
  { label: 'synthesize-map' }
)

phase('Walkthrough')

log(projectMap)

return { projectMap }
