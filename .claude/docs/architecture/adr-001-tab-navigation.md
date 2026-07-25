# ADR-001: Tab Navigation Without NavHost

## Status
Accepted

## Context
The app has 3 main tabs: 记录 (Home), 分析 (Chart), 计费 (Settings). Standard Jetpack Navigation uses `NavHost` with `composable()` destinations, but this has drawbacks:
- Tab switch triggers destroy/recreate of the composable tree
- `saveState`/`restoreState` adds complexity
- 350ms animation delay on tab switch
- ViewModel re-initialization on configuration change

## Decision
Use `AnimatedContent` with `when(tab)` instead of `NavHost`. ViewModels are cached at the Activity level via `hiltViewModel()`.

## Consequences
- **Positive**: Instant tab switch, no ViewModel re-creation, simpler code
- **Positive**: Predictive back gesture works naturally
- **Negative**: No deep-link-to-tab support (handled separately via deep links)
- **Negative**: No saved state across process death (acceptable for this app)
- **Negative**: All 3 ViewModels are initialized on first launch (slight memory overhead)

## Implementation
`AppNavGraph.kt` — single `Scaffold` with `NavigationBar` + `AnimatedContent`
