# TaskAppKMP (TaskProof)

Kotlin Multiplatform + Compose sample that demonstrates StateProof across:

- Desktop runtime UI
- Android runtime UI (with `StateProofNavHost`)
- iOS entrypoint wiring
- Generated test sync, diagrams, and viewer output

## Project Name

- Directory: `samples/TaskAppKMP`
- Gradle root name: `stateproof-task-app-kmp-sample`

## Modules

- `:composeApp` - shared KMP logic, state machine, UI, stateproof config
- `:androidApp` - Android launcher app that uses `:composeApp`

## Architecture Pattern

This sample now mirrors the iCages pattern:

- `AppState` is route-only (no embedded payload data)
- `MutableTaskAppStateData` holds reactive UI/business data in `MutableStateFlow`s
- `TaskAppViewModel` exposes:
  - `state: StateFlow<AppState>` for route/navigation
  - `stateData: TaskAppViewModelState` for reactive screen data
  - `onEvents(TaskAppViewEvent)` as the single UI interaction entrypoint
- State machine side effects call repositories only and mutate `MutableTaskAppStateData`
- Generated tests use introspection runtime helpers that provide both machine + state-data context

## Test Target Strategy (KMP)

This sample intentionally uses `desktopTest` as the primary generated-test target.

- Generated path tests: `desktopTest` (`composeApp/src/desktopTest/...`)
- Screenshot baseline tests: `desktopTest`
- Pure platform-agnostic logic tests: `commonTest`
- Optional Android visual parity checks: `androidTest`

Reason: current StateProof generation and viewer tooling are JVM-oriented.

## Generated Test Notes

- Generated `expectedTransitions` are authoritative.
- When sync can safely autogenerate event calls, generated test bodies are executable.
- When safe autogeneration is not possible, generated tests include `STATEPROOF:MANUAL_REQUIRED` with reasons.
- `scripts/hydrate-generated-tests.sh` is an optional advanced helper for this sample to auto-wire runtime-aware event dispatch for guarded/data-dependent flows.

## Module Coverage Map

| StateProof module | Where used |
|---|---|
| `stateproof-core` | `composeApp/commonMain` state machine DSL/runtime |
| `stateproof-compose` | `desktopMain`, `iosMain` Compose state collection |
| `stateproof-navigation` | `androidMain` `StateProofNavHost` |
| `stateproof-annotations` | `TaskProofIntrospection.kt` |
| `stateproof-ksp` | KSP metadata generation (`kspCommonMainMetadata`) |
| `stateproof-viewer-jvm` | `desktopTest` viewer generation tests |
| `stateproof-gradle-plugin` | `composeApp` (`stateproofScan`, `stateproofSyncAll`, diagrams, viewer) |

## Run Autonomous Flow

```bash
cd /Users/fshamim/Documents/dev/stateproof/samples/TaskAppKMP
./scripts/execute_autonomous.sh
```

## Manual Commands

```bash
cd /Users/fshamim/Documents/dev/stateproof/samples/TaskAppKMP
./gradlew :composeApp:stateproofScan -PstateproofClasspathConfig=desktopTestRuntimeClasspath
./gradlew :composeApp:stateproofSyncAll -PstateproofClasspathConfig=desktopTestRuntimeClasspath
# Optional advanced helper for manual-required generated tests in this sample:
./scripts/hydrate-generated-tests.sh
./gradlew :composeApp:desktopTest
./gradlew :composeApp:stateproofDiagrams -PstateproofClasspathConfig=desktopTestRuntimeClasspath
./gradlew :composeApp:stateproofViewer -PstateproofClasspathConfig=desktopTestRuntimeClasspath
./gradlew :composeApp:compileKotlinDesktop
./gradlew :androidApp:assembleDebug
```

If `desktopTestRuntimeClasspath` is unavailable in your environment, rerun StateProof tasks with:

```bash
-PstateproofClasspathConfig=jvmTestRuntimeClasspath
```

## Generated Outputs

- Scan report: `composeApp/build/stateproof/agent/project-scan.json`
- Synced tests: `composeApp/src/desktopTest/kotlin/generated/stateproof`
- Diagrams: `composeApp/build/stateproof/diagrams`
- Viewer: `composeApp/build/stateproof/viewer`
