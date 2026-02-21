# StateProof AI Agent Kit (Codex + Claude)

This folder is the AI integration contract for StateProof.

## Primary architecture contract (KMP Screens-as-States)

For Android/KMP projects using screen navigation, AI must implement the pattern from `docs/SCREENS_AS_STATES.md`:

1. Route-only state classes.
2. Reactive UI state data in `MutableStateFlow` container.
3. Shared KMP ViewModel-style facade exposing:
   - `state: StateFlow<RouteState>`
   - `stateData: ...`
   - `onEvents(...)`
4. Repository-only side effects in state machine.
5. `doNotTransition()` for in-place data updates.

Do not embed mutable business payload directly in route-state subclasses.

## Generated test contract for AI

When StateProof sync generates tests:

1. Treat `expectedTransitions` as source-of-truth.
2. If test has executable body, AI should only add helper reuse when needed.
3. If test contains `STATEPROOF:MANUAL_REQUIRED`, AI must implement the user section using a shared helper/harness strategy.
4. Preserve generated marker blocks and annotation metadata.
5. Avoid ad-hoc per-test bespoke setup when a shared helper can solve the same class of cases.

## Test target policy for KMP

Use this default policy unless project constraints override it:

1. Generated path tests: `desktopTest` (or JVM test source set configured via `testDir`).
2. Pure logic unit tests: `commonTest`.
3. Screenshot baseline: `desktopTest`.
4. Optional Android visual parity/screenshot layer: `androidTest`.

Reason: current StateProof generation/runtime tooling is JVM-oriented.

## Canonical coordinates (must use)

- Plugin ID: `io.github.fshamim.stateproof`
- Version: `0.8.0-alpha02`
- Core: `io.github.fshamim:stateproof-core-jvm:0.8.0-alpha02`
- Annotations: `io.github.fshamim:stateproof-annotations:0.8.0-alpha02`
- KSP processor: `io.github.fshamim:stateproof-ksp:0.8.0-alpha02`
- Viewer (test): `io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha02`
- Navigation (Android screens-as-states): `io.github.fshamim:stateproof-navigation:0.8.0-alpha02`

Do not use `io.stateproof:*` Maven coordinates. Source package names remain `io.stateproof.*`.

## Skill files

- Codex: `docs/ai/skills/codex/SKILL.md`
- Claude: `docs/ai/skills/claude/SKILL.md`

## `/stateproof` command contract (agent workflow)

### `/stateproof setup`

- Detect Gradle DSL (`build.gradle.kts` vs `build.gradle`)
- Detect target module (`:app` default or discovered module)
- Apply plugin/dependencies/config with canonical coordinates
- Use local StateProof docs as primary source
- Verify with:
  - `stateproofScan`
  - `stateproofSyncAll`
  - `stateproofDiagrams`
  - `stateproofViewer`

### `/stateproof scan`

- Run `./gradlew <module>:stateproofScan`
- Read `build/stateproof/agent/project-scan.json`

### `/stateproof tests`

- Run `./gradlew <module>:stateproofSyncAll`
- Ensure generated tests are in configured target test directory
- Report `STATEPROOF:MANUAL_REQUIRED` findings explicitly

### `/stateproof diagram`

- Run `./gradlew <module>:stateproofDiagrams`

### `/stateproof viewer`

- Run `./gradlew <module>:stateproofViewer`

### `/stateproof watch`

- Run `./gradlew <module>:stateproofWatch`
- Watch modes: `tests|diagram|viewer|all`

### `/stateproof migrate-screens`

- Follow `docs/playbooks/migration-existing-android-kmp.md`

### `/stateproof add-screen`

- Follow `docs/playbooks/add-screen-event-back.md`

## Prompt templates

- `docs/ai/prompts/setup-current-project.md`
- `docs/ai/prompts/integrate-existing-android-kmp.md`
- `docs/ai/prompts/integrate-existing-non-kmp.md`
- `docs/ai/prompts/add-screen-and-events.md`

## Replay references

- Non-KMP fallback replay: `docs/ai/replays/non-kmp-sample-replay.md`
- Consumer-specific replays should remain in consumer repository.
