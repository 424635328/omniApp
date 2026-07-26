# CLAUDE.md — EnergyFlow

> Household energy-consumption tracking Android application
> **Stack:** Kotlin · Jetpack Compose · Hilt · Room · Kotlin Multiplatform
> **Authoritative rules:** `.claude/shared/rules.md`
> **Agent protocol:** `.claude/docs/agents/protocol.md`

---

## 1. Request Routing

> **Do not write or modify code until the required skill or workflow has been loaded.**

Classify the user’s request using the routing table below.

```text
User request
├── Understand or explain code
│   └── Use: energyflow-acknowledge
│
├── Add, implement, or introduce a feature
│   ├── Default: energyflow-new-feature
│   └── If it affects 3+ files and crosses module boundaries:
│       └── Escalate to: workflow:feature-development
│
├── Fix a bug, investigate an error, or repair broken behavior
│   ├── Default: energyflow-diagnose
│   └── If it spans 3+ architectural layers:
│       └── Escalate to: workflow:bug-fix
│
├── Fix multiple bugs in one request
│   └── Use: workflow:multi-fix
│
├── Implement multiple features in one request
│   └── Use: workflow:multi-feature
│
├── Mixed task containing both bug fixes and feature work
│   └── Use: energyflow-parallel
│
├── Refactor code or improve architecture
│   └── Use: energyflow-refactor
│
├── Run, add, or update tests
│   └── Use: energyflow-test
│
├── Create a commit or prepare commit content
│   └── Use: energyflow-commit
│
├── Perform a pre-commit check or quick scan
│   └── Use: energyflow-quick-scan
│
├── Onboard a new contributor or explain the project structure
│   └── Use: energyflow-onboard
│
├── Modify the database schema or perform a migration
│   └── Use: energyflow-data-migration
│
├── Diagnose a compilation, Gradle, or build failure
│   └── Use: energyflow-build-debug
│
├── Review security, secrets, API keys, or sensitive data handling
│   └── Use: energyflow-security
│
├── Large-scale review or end-to-end task
│   ├── Comprehensive pre-commit review
│   │   └── Use: workflow:full-review
│   ├── End-to-end feature delivery
│   │   └── Use: workflow:feature-development
│   ├── Systematic bug investigation and repair
│   │   └── Use: workflow:bug-fix
│   └── Test, verify, and commit
│       └── Use: workflow:test-then-commit
│
└── Unclear or unmatched request
    └── Use: energyflow-acknowledge
```

### Routing Precedence

When a request matches more than one route, apply the following priority:

1. Explicit workflow requested by the user
2. Mixed or multi-task workflow
3. Large-scale workflow
4. Specialized skill
5. `energyflow-acknowledge` as the fallback

Do not split a clearly unified task across unrelated skills when an applicable workflow already coordinates the full process.

---

## 2. Skill Loading Rules

Before making code changes:

1. Identify the correct skill or workflow.
2. Load its instructions.
3. Read any project files required by that skill.
4. Confirm the affected modules and architectural layers.
5. Only then inspect, edit, or generate code.

Loading a skill is not optional merely because the requested change appears straightforward.

### Fast-Path Exceptions

Skill loading may be skipped only when at least one of the following conditions is true:

* The change is strictly limited to one line, such as:

  * correcting a typo;
  * changing a string literal;
  * adding or removing a single import.
* The user explicitly says to make the change directly without reading project documentation.
* The same skill has already been loaded and applied earlier in the current conversation.

The fast path does not apply when the change affects behavior, architecture, persistence, dependency injection, or shared KMP code.

---

## 3. Non-Negotiable Engineering Rules

Detailed requirements are defined in:

```text
.claude/shared/rules.md
```

The rules below are mandatory summaries, not replacements for the authoritative document.

### 3.1 Kotlin Multiplatform

Code under:

```text
shared/src/commonMain/
```

must remain platform-independent.

Allowed date and time API:

```kotlin
kotlinx.datetime
```

Prohibited APIs and dependencies include:

```kotlin
java.time.*
java.util.*
android.*
```

Do not introduce Android-specific types, JVM-only APIs, or platform implementations into `commonMain`.

### 3.2 Hilt

Use constructor injection for injectable classes:

```kotlin
@Singleton
class ExampleRepository @Inject constructor(
    private val dependency: Dependency,
)
```

ViewModels must use:

```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    ...
) : ViewModel()
```

Do not manually instantiate Hilt-managed dependencies unless explicitly required by a framework boundary.

### 3.3 Jetpack Compose

Use the project typography and theme system.

Required conventions:

```kotlin
MonoFontFamily
collectAsStateWithLifecycle()
```

Rules:

* Use theme-defined colors.
* Do not hardcode hexadecimal colors in composables.
* Collect observable UI state with lifecycle awareness.
* Keep business logic outside composables.
* Prefer immutable UI state and unidirectional data flow.

### 3.4 Energy and Meter Data

`MeterRecord` values may be nullable.

When a calculation explicitly requires a numeric fallback, use:

```kotlin
value ?: 0.0
```

However:

```text
null != 0.0
```

Preserve this semantic distinction whenever missing data and an actual zero reading have different meanings.

Meter readings are cumulative values. Consumption over a period is calculated as:

```text
newer reading - older reading
```

Do not treat cumulative readings as independent interval consumption values.

Before subtracting readings, verify:

* both readings belong to the same meter;
* the timestamps are ordered correctly;
* the newer reading is not older than the baseline;
* rollover, replacement, reset, or invalid negative usage is handled appropriately.

### 3.5 Room Database

The application currently uses:

```kotlin
fallbackToDestructiveMigration()
```

Therefore, an unsupported schema version change may delete all locally stored data.

Any Room schema modification must be treated as a destructive-data-risk task.

Before changing an entity, DAO schema, index, relation, or database version:

1. Load `energyflow-data-migration`.
2. Identify whether existing user data must be preserved.
3. State the destructive-migration impact clearly.
4. Add an explicit migration when preservation is required.
5. Update and run relevant database tests.

Never describe a schema change as safe without checking the configured migration behavior.

---

## 4. Build and Test Commands

Run commands from the repository root.

### Compile the Android application

```bash
./gradlew :app:compileDebugKotlin
```

### Compile the Android target of the shared KMP module

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

### Run all debug unit tests for the application

```bash
./gradlew :app:testDebugUnitTest
```

### Run a specific test class or test pattern

```bash
./gradlew :app:testDebugUnitTest --tests "fully.qualified.TestClassName"
```

### Rebuild the debug APK and bypass potentially stale task outputs

Use this when generated Hilt code or cached build outputs appear corrupted or stale:

```bash
./gradlew :app:assembleDebug --rerun-tasks
```

---

## 5. Verification Requirements

After modifying code, run the narrowest relevant verification first, followed by broader checks when warranted.

Recommended order:

```text
1. Compile the directly affected module
2. Run the directly affected test class
3. Run the module's full unit-test suite
4. Compile dependent modules
5. Run broader workflow-specific checks
```

Minimum expectations by change type:

| Change type               | Minimum verification                                                          |
| ------------------------- | ----------------------------------------------------------------------------- |
| UI-only Compose change    | Compile `:app` and run relevant UI or ViewModel tests                         |
| ViewModel or domain logic | Compile affected modules and run targeted unit tests                          |
| Shared KMP code           | Compile `:shared:compileDebugKotlinAndroid` and test affected consumers       |
| Hilt dependency graph     | Compile `:app`; use `--rerun-tasks` if generated-code caching causes failures |
| Room schema or DAO        | Compile, run database tests, and verify migration behavior                    |
| Refactor                  | Run tests covering preserved behavior                                         |
| Bug fix                   | Add or update a regression test whenever practical                            |

Do not claim that a change works unless the relevant verification was actually completed. If a command cannot be run, clearly state what remains unverified.

---

## 6. Change Discipline

When editing the repository:

* Keep changes scoped to the user’s request.
* Avoid unrelated cleanup unless it is necessary for correctness.
* Follow existing package, naming, formatting, and architectural conventions.
* Search for existing implementations before introducing new abstractions.
* Prefer extending established patterns over creating parallel systems.
* Do not silently change public behavior, persistence semantics, or data interpretation.
* Do not add dependencies without explaining why existing project capabilities are insufficient.
* Never expose secrets, API keys, credentials, or sensitive user data.

For large changes, summarize:

```text
Affected modules
Affected architectural layers
Key implementation decisions
Data or migration risks
Tests performed
Remaining limitations
```

---

## 7. Completion Criteria

A task is complete only when:

1. The correct skill or workflow was followed.
2. The requested behavior was implemented or analyzed.
3. Relevant project rules were respected.
4. Appropriate compilation or tests were run.
5. Failures and unverified areas were disclosed.
6. The final response explains the result without overstating certainty.

When any criterion cannot be satisfied, provide the completed portion and explicitly identify the remaining gap.
