# Screens-as-States Pattern

StateProof Navigation treats **screens as states** — a powerful pattern that makes your app's navigation provably correct and exhaustively testable.

## The Problem with Traditional Navigation

In typical Android apps, navigation is imperative and scattered:

```kotlin
// Traditional approach - navigation calls everywhere
Button(onClick = { navController.navigate("settings") }) { ... }

// Back handling is separate
BackHandler { navController.popBackStack() }

// Deep links are configured separately
// Animation logic is separate
// State is managed separately
```

This leads to:
- **Untestable navigation** — Hard to verify all navigation paths
- **Scattered logic** — Navigation, state, and UI are intertwined
- **Implicit state** — The "current screen" is implicit in the back stack
- **Manual back handling** — Must remember to handle back everywhere

## The Solution: Screens-as-States

With StateProof, screens ARE states in your state machine:

```kotlin
sealed class States {
    object Initial : States()
    object Home : States()
    object Settings : States()
    object LoadPoint : States()
    object Measurement : States()
}

sealed class Events {
    object OnAppStart : Events()
    object OnBack : Events()
    object OnToSettings : Events()
    object OnToLoadPoint : Events()
}
```

Navigation becomes a **state transition**:

```kotlin
val stateMachine = StateMachine<States, Events> {
    initialState(States.Initial)

    state<States.Initial> {
        on<Events.OnAppStart> { transitionTo(States.Home) }
    }

    state<States.Home> {
        on<Events.OnToSettings> { transitionTo(States.Settings) }
        on<Events.OnToLoadPoint> { transitionTo(States.LoadPoint) }
        on<Events.OnBack> { /* exit app or show dialog */ }
    }

    state<States.Settings> {
        on<Events.OnBack> { transitionTo(States.Home) }
    }

    state<States.LoadPoint> {
        on<Events.OnBack> { transitionTo(States.Home) }
    }
}
```

## Real-World Example: Banking Transaction Workflow

### Problem Statement

Banking transaction flows require deterministic behavior:
- A transfer request must not silently disappear or execute twice.
- Validation, submission, OTP, and failure recovery must follow explicit rules.
- The workflow should be reviewable as behavior, not hidden across UI callbacks and network code.

For modeled transaction behavior, StateProof gives complete path coverage of the state graph and keeps generated tests synced as the flow evolves.

Runtime queue semantics (emitted events are prioritized with queue-front insertion) are shown here:
[`How Event Processing Works`](../README.md#how-event-processing-works-queue--side-effect-priority)

### State + Event Model (with Data Class Events)

```kotlin
sealed class TransferState {
    object Form : TransferState()
    object Submitting : TransferState()
    data class AwaitingOtp(val transactionId: String) : TransferState()
    data class Success(val receiptId: String) : TransferState()
    data class Failed(val reason: String) : TransferState()
}

sealed class TransferEvent {
    data class OnSubmit(
        val fromAccountId: String,
        val toAccountId: String,
        val amountCents: Long,
    ) : TransferEvent()
    data class OnOtpEntered(val code: String) : TransferEvent()
    object OnBack : TransferEvent()
    data class OnOtpRequired(val transactionId: String) : TransferEvent()
    data class OnTransferCompleted(val receiptId: String) : TransferEvent()
    data class OnTransferFailed(val reason: String) : TransferEvent()
}
```

### State Machine DSL

```kotlin
val transferMachine = stateMachine<TransferState, TransferEvent>(TransferState.Form) {
    state<TransferState.Form> {
        on<TransferEvent.OnSubmit> {
            condition("amount > 0") { _, event -> event.amountCents > 0 } then {
                transitionTo(TransferState.Submitting)
                sideEffect { event ->
                    val response = bankApi.createTransfer(
                        fromAccountId = event.fromAccountId,
                        toAccountId = event.toAccountId,
                        amountCents = event.amountCents,
                    )
                    when (response) {
                        is TransferResponse.OtpRequired ->
                            TransferEvent.OnOtpRequired(response.transactionId)
                        is TransferResponse.Success ->
                            TransferEvent.OnTransferCompleted(response.receiptId)
                        is TransferResponse.Failure ->
                            TransferEvent.OnTransferFailed(response.reason)
                    }
                } emits (
                    "otp_required" to TransferEvent.OnOtpRequired::class,
                    "transfer_completed" to TransferEvent.OnTransferCompleted::class,
                    "transfer_failed" to TransferEvent.OnTransferFailed::class,
                )
            }
            otherwise { doNotTransition() }
        }
    }

    state<TransferState.Submitting> {
        on<TransferEvent.OnOtpRequired> { event ->
            transitionTo(TransferState.AwaitingOtp(event.transactionId))
        }
        on<TransferEvent.OnTransferCompleted> { event ->
            transitionTo(TransferState.Success(event.receiptId))
        }
        on<TransferEvent.OnTransferFailed> { event ->
            transitionTo(TransferState.Failed(event.reason))
        }
    }

    state<TransferState.AwaitingOtp> {
        on<TransferEvent.OnOtpEntered> {
            transitionTo(TransferState.Submitting)
            sideEffect { event ->
                val verify = bankApi.verifyOtp(event.code)
                if (verify.isApproved) {
                    TransferEvent.OnTransferCompleted(verify.receiptId)
                } else {
                    TransferEvent.OnTransferFailed("OTP verification failed")
                }
            } emits (
                "transfer_completed" to TransferEvent.OnTransferCompleted::class,
                "transfer_failed" to TransferEvent.OnTransferFailed::class,
            )
        }
        on<TransferEvent.OnBack> { transitionTo(TransferState.Form) }
    }

    state<TransferState.Failed> {
        on<TransferEvent.OnBack> { transitionTo(TransferState.Form) }
    }
}
```

## Benefits

### 1. Exhaustive Test Coverage

Every possible navigation path is enumerable:

```kotlin
// StateProof generates these automatically
val paths = enumerator.generateTestCases()
// [Initial → Home → Settings → Home]
// [Initial → Home → LoadPoint → Home]
// [Initial → Home → Settings → Home → LoadPoint → ...]
```

### 2. Single Source of Truth

The state machine defines:
- What screens exist
- How to navigate between them
- What happens on back
- Side effects during transitions

### 3. Automatic Back Handling

Back is just another event:

```kotlin
StateProofNavHost(...) {
    onBack { Events.OnBack }  // That's it!
    // ...
}
```

### 4. Declarative Navigation

No imperative `navigate()` calls. Dispatch events, state changes, navigation follows:

```kotlin
// Instead of: navController.navigate("settings")
stateMachine.onEvent(Events.OnToSettings)
// StateProofNavHost automatically navigates to Settings
```

## Implementation with StateProofNavHost

```kotlin
@Composable
fun MyApp() {
    val navController = rememberNavController()
    val stateMachine = remember { createStateMachine() }

    StateProofNavHost(
        stateMachine = stateMachine,
        navController = navController,
    ) {
        // Map states to screens
        screen<States.Home>("home") { state ->
            HomeScreen(
                onSettingsClick = { stateMachine.onEvent(Events.OnToSettings) },
                onLoadPointClick = { stateMachine.onEvent(Events.OnToLoadPoint) },
            )
        }

        screen<States.Settings>("settings") { state ->
            SettingsScreen()
        }

        screen<States.LoadPoint>("loadpoint") { state ->
            LoadPointScreen()
        }

        // Back handling
        onBack { Events.OnBack }

        // Animations
        defaultAnimations(
            enter = StateProofAnimations.slideInFromRight,
            exit = StateProofAnimations.slideOutToLeft,
            popEnter = StateProofAnimations.slideInFromLeft,
            popExit = StateProofAnimations.slideOutToRight,
        )
    }
}
```

## Pattern: Hierarchical States for Sub-Screens

For screens with sub-states (like a measurement flow):

```kotlin
sealed class States {
    object Home : States()

    // Measurement has sub-states
    sealed class Measurement : States() {
        object Idle : Measurement()
        object Measuring : Measurement()
        object Complete : Measurement()
    }
}
```

All `Measurement` sub-states map to the same screen, but the screen renders differently:

```kotlin
screen<States.Measurement>("measurement") { state ->
    when (state) {
        is States.Measurement.Idle -> IdleUI()
        is States.Measurement.Measuring -> MeasuringUI()
        is States.Measurement.Complete -> CompleteUI()
    }
}
```

## Pattern: Passing Data Between Screens

States can carry data:

```kotlin
sealed class States {
    object Home : States()
    data class LoadPoint(val pointId: String) : States()
    data class Measurement(val loadPoint: LoadPointData) : States()
}
```

The state is passed to the screen:

```kotlin
screen<States.LoadPoint> { state ->
    LoadPointScreen(pointId = state.pointId)
}

screen<States.Measurement> { state ->
    MeasurementScreen(loadPoint = state.loadPoint)
}
```

## Pattern: Side Effects During Navigation

Side effects run during state transitions, not in the UI:

```kotlin
state<States.Home> {
    on<Events.OnToLoadPoint> { event ->
        transitionTo(States.LoadPoint(event.pointId)) {
            // Side effect: load data from database
            val data = loadPointRepository.get(event.pointId)
            updateUIState(data)
            null  // No follow-up event
        }
    }
}
```

## Comparison: Before and After

### Before (Traditional)

```kotlin
// MainActivity.kt
@Composable
fun App() {
    val navController = rememberNavController()
    var currentData by remember { mutableStateOf<Data?>(null) }

    NavHost(navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onSettingsClick = { navController.navigate("settings") },
                onItemClick = { id ->
                    currentData = loadData(id)  // Where does this go?
                    navController.navigate("detail/$id")
                }
            )
        }
        composable("settings") { SettingsScreen() }
        composable("detail/{id}") { DetailScreen(currentData) }
    }

    BackHandler {
        if (!navController.popBackStack()) {
            // Show exit dialog? Exit app?
        }
    }
}
```

### After (Screens-as-States)

```kotlin
// StateMachine.kt - Single source of truth
val sm = StateMachine<States, Events> {
    initialState(States.Home)

    state<States.Home> {
        on<Events.OnToSettings> { transitionTo(States.Settings) }
        on<Events.OnSelectItem> { event ->
            transitionTo(States.Detail(event.id)) {
                loadData(event.id)  // Side effect in state machine
            }
        }
        on<Events.OnBack> { showExitDialog() }
    }

    state<States.Settings> {
        on<Events.OnBack> { transitionTo(States.Home) }
    }

    state<States.Detail> {
        on<Events.OnBack> { transitionTo(States.Home) }
    }
}

// MainActivity.kt - Just wiring
@Composable
fun App() {
    StateProofNavHost(stateMachine, navController) {
        screen<States.Home> { HomeScreen(::onEvent) }
        screen<States.Settings> { SettingsScreen() }
        screen<States.Detail> { state -> DetailScreen(state.data) }
        onBack { Events.OnBack }
    }
}
```

## Testing the Navigation

With screens-as-states, navigation is fully testable:

```kotlin
@Test
fun `home to settings and back`() = runBlocking {
    val sm = createStateMachine()

    sm.onEvent(Events.OnAppStart)
    sm.awaitIdle()
    assertEquals(States.Home, sm.currentState)

    sm.onEvent(Events.OnToSettings)
    sm.awaitIdle()
    assertEquals(States.Settings, sm.currentState)

    sm.onEvent(Events.OnBack)
    sm.awaitIdle()
    assertEquals(States.Home, sm.currentState)
}
```

StateProof generates these tests automatically from the state graph!

## Summary

| Aspect | Traditional | Screens-as-States |
|--------|-------------|-------------------|
| Navigation calls | Imperative, scattered | Events dispatched to state machine |
| Current screen | Implicit in NavController | Explicit in state machine |
| Back handling | Manual, per-screen | Single `onBack` event |
| Side effects | Mixed with UI | In state transitions |
| Testability | Hard | Automatic test generation |
| Single source of truth | No | Yes (state machine) |

The screens-as-states pattern makes your navigation **provable, testable, and maintainable**.
