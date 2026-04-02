# StateProof Integration Skill (Codex)

## Purpose

Integrate StateProof with deterministic checkpoints while preserving production behavior.

## Trigger

Use this skill when asked to:

- integrate/setup StateProof
- migrate Android/KMP app to screens-as-states
- add screen/event/back flow
- run scan/tests/diagram/viewer/watch workflows

## Required architecture for Android/KMP screens-as-states

Follow `docs/SCREENS_AS_STATES.md` exactly:

1. route-only state model (no payload in route state subclasses)
2. separate reactive UI state data (`MutableStateFlow` container)
3. shared KMP ViewModel-like facade with `state`, `stateData`, `onEvents(...)`
4. repository boundary for side effects
5. `doNotTransition()` for in-place content updates

## Workflow (must follow in order)

1. **Scan**
   - Run `./gradlew <module>:stateproofScan`
   - Read `build/stateproof/agent/project-scan.json`
2. **Classify**
   - `SCREENS_AS_STATES` => Android/KMP migration flow
   - else => `STATE_MACHINE_ONLY`
3. **Plan**
   - list minimal file edits + verification commands
4. **Apply**
   - implement in small safe slices
5. **Verify**
   - run `stateproofSyncAll`, `stateproofDiagrams`, `stateproofViewer`
   - for screenshot-enabled machines run `stateproofScreenshotsSync`, `stateproofScreenshotsRecord`, `stateproofScreenshotsVerify`
   - verify generated outputs are non-empty

## Generated test handling rules

1. `expectedTransitions` is authoritative.
2. If generated body is executable, keep it and only factor shared helpers if needed.
3. If `STATEPROOF:MANUAL_REQUIRED` is present, implement via shared helper/harness.
4. Never edit the generated expected-transition marker block manually.
5. Prefer one helper for repeated runtime setup over custom per-test logic.

## Test target policy

Default policy:

1. generated StateProof paths -> JVM/Android unit tests (`testDir` / `androidTestDir`)
2. pure platform-agnostic logic -> `commonTest`
3. screenshot baseline (MVP) -> Android unit tests with Paparazzi
4. optional device-level screenshot parity -> separate layer

## Canonical setup coordinates

- Plugin: `id("io.github.fshamim.stateproof") version "0.8.0-alpha02"`
- Core: `implementation("io.github.fshamim:stateproof-core-jvm:0.8.0-alpha02")`
- Annotations: `implementation("io.github.fshamim:stateproof-annotations:0.8.0-alpha02")`
- KSP: `ksp("io.github.fshamim:stateproof-ksp:0.8.0-alpha02")`
- Viewer (test): `testImplementation("io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha02")`
- Screenshot (test): `testImplementation("io.github.fshamim:stateproof-screenshot-jvm:0.8.0-alpha02")`
- Android navigation: `implementation("io.github.fshamim:stateproof-navigation:0.8.0-alpha02")` when needed

Do not use `io.stateproof:*` Maven coordinates.

## Command contract

- `/stateproof setup` -> apply plugin/deps/config (DSL aware, local docs first)
- `/stateproof scan` -> `stateproofScan`
- `/stateproof tests` -> `stateproofSyncAll`
- `/stateproof diagram` -> `stateproofDiagrams`
- `/stateproof viewer` -> `stateproofViewer`
- `/stateproof screenshots-sync` -> `stateproofScreenshotsSync`
- `/stateproof screenshots-record` -> `stateproofScreenshotsRecord`
- `/stateproof screenshots-verify` -> `stateproofScreenshotsVerify`
- `/stateproof watch` -> `stateproofWatch`
- `/stateproof migrate-screens` -> use `docs/playbooks/migration-existing-android-kmp.md`
- `/stateproof add-screen` -> use `docs/playbooks/add-screen-event-back.md`

## Guardrails

- detect Gradle DSL before edits
- prefer factory/KSP auto-discovery over manual maps
- keep transition intent explicit (guards and emitted-event metadata)
- preserve user test implementations
- for JVM/non-KMP apps, avoid forcing navigation integration
