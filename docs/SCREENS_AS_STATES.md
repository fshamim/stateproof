# Screens-as-States for Kotlin Multiplatform (StateProof)

This document defines the canonical StateProof architecture for Kotlin Multiplatform apps.

Use this as the source of truth for app structure, navigation/state boundaries, and generated-test strategy.

## Core Rule Set

1. Route state is separate from UI/business data.
2. `AppState` is route-only (no embedded payload fields).
3. Screen data lives in a reactive UI state container (`MutableStateFlow`-backed) owned by a shared KMP ViewModel-like class.
4. UI sends interactions through a single `onEvents(...)` entrypoint.
5. State machine side effects use repositories only (no direct DB/framework coupling).
6. In-place content updates use `doNotTransition()` to avoid route changes.

## Architecture Blueprint

### 1) Route-only state model

```kotlin
sealed interface AppState {
    data object Splash : AppState
    data object Login : AppState
    data object LoadingTasks : AppState
    data object TaskList : AppState
    data object TaskDetail : AppState
    data object AuthError : AppState
}
```

`AppState` represents navigation/route only.

### 2) Reactive UI state data

```kotlin
data class MutableTaskAppStateData(
    val _authToken: MutableStateFlow<String> = MutableStateFlow(""),
    val _authErrorReason: MutableStateFlow<String?> = MutableStateFlow(null),
    val _tasks: MutableStateFlow<List<TaskItem>> = MutableStateFlow(emptyList()),
    val _selectedTaskId: MutableStateFlow<String?> = MutableStateFlow(null),
)
```

Expose read-only `StateFlow`s to UI via a `TaskAppViewModelState` snapshot type.

### 3) Shared KMP ViewModel-style facade

```kotlin
class TaskAppViewModel(
    private val runtime: TaskProofRuntime,
) {
    val state: StateFlow<AppState> = runtime.stateMachine.state
    val stateData: TaskAppViewModelState = ...

    fun onEvents(event: TaskAppViewEvent) { ... }
    fun close() { ... }
}
```

Use this same facade from Android/Desktop/iOS entrypoints.

### 4) Event routing

```kotlin
sealed interface TaskAppViewEvent
sealed interface AppEvent : TaskAppViewEvent
sealed interface LocalUiEvent : TaskAppViewEvent
```

- `AppEvent`: forwarded to state machine.
- `LocalUiEvent`: handled inline in ViewModel (local state cleanup, UI-only behavior).

This keeps routing extensible for future multi-machine compositions.

### 5) Repository-driven state machine side effects

State transitions should call repositories and then mutate `MutableTaskAppStateData`.

```kotlin
state<AppState.TaskList> {
    on<AppEvent.OnToggleTask> {
        doNotTransition()
        sideEffect { event ->
            when (val result = taskRepository.toggleTask(token, event.id)) {
                is TaskToggleResult.Success -> AppEvent.OnTaskToggled(result.id, result.completed)
                is TaskToggleResult.Failure -> AppEvent.OnTaskSaveFailed(result.reason)
            }
        }
    }
}
```

`doNotTransition()` means route stays on `TaskList`; only content changes.

## Navigation and Animation Semantics

- Route transitions animate according to destination mapping.
- `doNotTransition()` should not trigger screen-route animation.
- If UI still animates, verify that route state is unchanged and only `stateData` flows changed.

## Testing Model

StateProof-generated tests assert transition logs. Runtime harness logic can be auto-generated only when event invocation is compile-safe.

### Generated tests authoring contract

1. `expectedTransitions` inside `STATEPROOF:EXPECTED` is authoritative.
2. If all path events are safely invokable, generator emits executable body.
3. If any event is not safely invokable (or path is guarded/side-effect-complex), generator emits:
   - commented scaffold body
   - `STATEPROOF:MANUAL_REQUIRED` marker with reasons
4. Preserve edits in user section only; generated section is managed by sync.
5. Prefer helper-based customization (shared harness/util) over per-test custom logic.

### Manual-required marker

Generated tests may include:

```kotlin
// STATEPROOF:MANUAL_REQUIRED - Auto body unavailable; review required
// - OnSubmit: constructor requires arguments
// - OnRetry: guarded transition 'token.exists' needs explicit test setup
```

This marker is stable and should be treated as an explicit implementation task for AI/user.

## Test Target Policy

For this repository and current StateProof toolchain:

1. Generated path tests default to JVM-oriented source sets (`test` / configured `testDir`).
2. `commonTest` is for hand-written pure logic tests (platform-agnostic code).
3. Screenshot testing MVP uses Android unit tests with Paparazzi (`stateproofScreenshotsRecord`/`stateproofScreenshotsVerify`).
4. Additional device-level screenshot parity is optional and layered separately.

### Why not `commonTest` for generated path tests today

- Current generator runtime uses JVM reflection in CLI workflows.
- Viewer and related generation tooling are JVM-first.
- Screenshot generation/sync workflow is Android-first in the OSS MVP.

## Implementation Checklist (for new screens/flows)

1. Add route-only state object(s) to `AppState`.
2. Add/route events in `TaskAppViewEvent`.
3. Add transitions in state machine.
4. Keep data in `MutableTaskAppStateData` flows, not in state classes.
5. Update repositories and side effects.
6. Ensure `doNotTransition()` for in-place list/detail content updates.
7. Sync generated tests and inspect any `STATEPROOF:MANUAL_REQUIRED` markers.
8. Keep reusable harness helpers in one place and avoid duplicated per-test custom code.
