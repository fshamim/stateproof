# StateProof

A Kotlin Multiplatform state machine library with exhaustive test generation.

## Core Value Proposition

**The state graph drives your behavioral test suite.** StateProof's DFS traversal algorithm enumerates reachable paths in your modeled state machine, and each path becomes a generated test case.

This reframes the testing workflow:
- **Traditional**: implement first -> manually backfill tests -> path coverage remains uncertain
- **TDD**: red/green/refactor gives strong local correctness for chosen examples
- **StateProof**: model states/events -> enumerate graph paths -> sync generated suite as the model evolves

StateProof does not replace unit, integration, visual, or runtime testing; it provides near-exhaustive coverage for **modeled UI behavior**.

## Overview

### Workflow Comparison

| Approach | Goal unit | Feedback loop | Done signal | Typical blind spot |
|----------|-----------|---------------|-------------|--------------------|
| Traditional | Feature code | Manual tests added after implementation | Team judgment + exploratory confidence | Missed path combinations |
| TDD | Example-level behavior | Red -> Green -> Refactor | Chosen examples pass | State-space/path completeness is still manual |
| StateProof | Modeled state graph | Model update -> regenerate/sync suite | Reachable modeled paths are covered and green | Unmodeled concerns (visual/runtime/integration) |

### Every Screen as a State

```mermaid
stateDiagram-v2
    [*] --> Login: OnAppStart
    Login --> Authenticating: OnLoginSubmitted(username, password)
    Authenticating --> LoadingHomeData: emits OnAuthSucceeded(token)
    Authenticating --> LoginError: emits OnAuthFailed(reason)
    LoadingHomeData --> Home: emits OnHomeDataLoaded(summary)
    LoadingHomeData --> LoginError: emits OnHomeDataLoadFailed(reason)
    LoginError --> Login: OnRetryLogin
    Home --> Login: OnLogout
```

### Quick Start for This Flow (StateProof DSL)

```kotlin
data class HomeSummary(
    val firstName: String,
    val unreadCount: Int,
)

sealed class AuthState {
    object Login : AuthState()
    object Authenticating : AuthState()
    object LoadingHomeData : AuthState()
    data class Home(val summary: HomeSummary) : AuthState()
    data class LoginError(val reason: String) : AuthState()
}

sealed class AuthEvent {
    object OnAppStart : AuthEvent()
    data class OnLoginSubmitted(val username: String, val password: String) : AuthEvent()
    data class OnAuthSucceeded(val token: String) : AuthEvent()
    data class OnAuthFailed(val reason: String) : AuthEvent()
    data class OnHomeDataLoaded(val summary: HomeSummary) : AuthEvent()
    data class OnHomeDataLoadFailed(val reason: String) : AuthEvent()
    object OnRetryLogin : AuthEvent()
    object OnLogout : AuthEvent()
}

sealed class AuthResult {
    data class Success(val token: String) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
}

sealed class HomeLoadResult {
    data class Success(val summary: HomeSummary) : HomeLoadResult()
    data class Failure(val reason: String) : HomeLoadResult()
}

interface AuthRepository {
    suspend fun login(username: String, password: String): AuthResult
}

interface HomeRepository {
    suspend fun loadHomeSummary(token: String): HomeLoadResult
}

val authRepository: AuthRepository = TODO()
val homeRepository: HomeRepository = TODO()

val machine = stateMachine<AuthState, AuthEvent>(AuthState.Login) {
    state<AuthState.Login> {
        on<AuthEvent.OnLoginSubmitted> {
            // Guarded branch keeps invalid input as a deterministic self-transition.
            condition("credentials present") { _, event ->
                event.username.isNotBlank() && event.password.isNotBlank()
            } then {
                transitionTo(AuthState.Authenticating)
                sideEffect { event ->
                    when (val result = authRepository.login(event.username, event.password)) {
                        is AuthResult.Success -> AuthEvent.OnAuthSucceeded(result.token)
                        is AuthResult.Failure -> AuthEvent.OnAuthFailed(result.reason)
                    }
                } emits (
                    "auth_succeeded" to AuthEvent.OnAuthSucceeded::class,
                    "auth_failed" to AuthEvent.OnAuthFailed::class,
                )
            }
            otherwise { doNotTransition() }
        }
    }

    state<AuthState.Authenticating> {
        on<AuthEvent.OnAuthSucceeded> {
            transitionTo(AuthState.LoadingHomeData)
            // Side effect emits the next backend result event into the state machine.
            sideEffect { event ->
                when (val result = homeRepository.loadHomeSummary(event.token)) {
                    is HomeLoadResult.Success -> AuthEvent.OnHomeDataLoaded(result.summary)
                    is HomeLoadResult.Failure -> AuthEvent.OnHomeDataLoadFailed(result.reason)
                }
            } emits (
                "home_data_loaded" to AuthEvent.OnHomeDataLoaded::class,
                "home_data_failed" to AuthEvent.OnHomeDataLoadFailed::class,
            )
        }
        on<AuthEvent.OnAuthFailed> { event -> transitionTo(AuthState.LoginError(event.reason)) }
    }

    state<AuthState.LoadingHomeData> {
        on<AuthEvent.OnHomeDataLoaded> { event -> transitionTo(AuthState.Home(event.summary)) }
        on<AuthEvent.OnHomeDataLoadFailed> { event -> transitionTo(AuthState.LoginError(event.reason)) }
    }

    state<AuthState.LoginError> {
        on<AuthEvent.OnRetryLogin> { transitionTo(AuthState.Login) }
    }

    state<AuthState.Home> {
        on<AuthEvent.OnLogout> { transitionTo(AuthState.Login) }
    }
}
```

### How Event Processing Works (Queue + Side-Effect Priority)

```mermaid
sequenceDiagram
    participant UI as "UI/Caller"
    participant SM as "StateMachine.onEvent"
    participant Q as "eventQueue (ArrayDeque)"
    participant P as "Event Processor Loop"
    participant FX as "Transition SideEffect"

    UI->>SM: onEvent(OnLoginSubmitted(alice))
    SM->>Q: addLast(OnLoginSubmitted(alice))
    SM->>P: signal processing

    UI->>SM: onEvent(OnLoginSubmitted(bob))
    SM->>Q: addLast(OnLoginSubmitted(bob))
    SM->>P: signal processing

    P->>Q: removeFirst() returns OnLoginSubmitted(alice)
    P->>P: apply transition (state updated)
    P->>FX: run sideEffect(OnLoginSubmitted(alice))
    FX-->>P: emit OnAuthSucceeded(token)
    P->>Q: addFirst(OnAuthSucceeded(token))
    P->>P: signal processing

    Note over Q: Emitted event is inserted at the queue front

    P->>Q: removeFirst() returns OnAuthSucceeded(token)
    P->>P: process emitted event first

    P->>Q: removeFirst() returns OnLoginSubmitted(bob)
    P->>P: process next queued event
```

- Transition is applied before side-effect execution.
- Side-effect emitted event is pushed to queue front.
- This priority applies within modeled runtime behavior and does not replace integration/visual tests.

### Complex Workflow Example: Bank Transaction Recovery

```mermaid
stateDiagram-v2
    [*] --> TransferForm: OnOpenTransfer
    TransferForm --> SubmittingTransfer: OnSubmitTransfer(request)
    SubmittingTransfer --> AwaitingSettlement: emits OnTransferSubmitted(reference)
    SubmittingTransfer --> TransferFailed: emits OnTransferRejected(reason)
    SubmittingTransfer --> RecoveryCheck: emits OnNetworkLost(reference)
    AwaitingSettlement --> TransferCompleted: emits OnSettlementConfirmed(receiptId)
    AwaitingSettlement --> RecoveryCheck: emits OnSettlementTimeout(reference)
    RecoveryCheck --> AwaitingSettlement: emits OnBankConfirmed(reference)
    RecoveryCheck --> TransferCanceled: emits OnBankCanceled(reference)
    RecoveryCheck --> ManualReview: emits OnBankStatusUnknown(reason)
    ManualReview --> RecoveryCheck: OnRetryRecovery(reference)
    TransferFailed --> TransferForm: OnRetry
    TransferCanceled --> TransferForm: OnRetry
```

```kotlin
// TransferState and TransferEvent carry reference/reason payloads for each branch.
val transferMachine = stateMachine<TransferState, TransferEvent>(TransferState.TransferForm) {
    state<TransferState.TransferForm> {
        on<TransferEvent.OnSubmitTransfer> {
            transitionTo(TransferState.SubmittingTransfer)
            // Side effect emits submit outcomes from the bank API.
            sideEffect { event ->
                when (val result = bankApi.submit(event.request)) {
                    is SubmitResult.Accepted -> TransferEvent.OnTransferSubmitted(result.reference)
                    is SubmitResult.Rejected -> TransferEvent.OnTransferRejected(result.reason)
                    is SubmitResult.NetworkLost -> TransferEvent.OnNetworkLost(result.reference)
                }
            } emits (
                "transfer_submitted" to TransferEvent.OnTransferSubmitted::class,
                "transfer_rejected" to TransferEvent.OnTransferRejected::class,
                "network_lost" to TransferEvent.OnNetworkLost::class,
            )
        }
    }

    state<TransferState.SubmittingTransfer> {
        on<TransferEvent.OnTransferSubmitted> { event ->
            transitionTo(TransferState.AwaitingSettlement(event.reference))
        }
        on<TransferEvent.OnNetworkLost> { event ->
            transitionTo(TransferState.RecoveryCheck(event.reference))
        }
    }

    state<TransferState.RecoveryCheck> {
        on<TransferEvent.OnRetryRecovery> {
            doNotTransition()
            // Recovery side effect asks the bank for final confirmation or cancelation.
            sideEffect { event ->
                when (val status = bankApi.checkStatus(event.reference)) {
                    is StatusResult.Confirmed -> TransferEvent.OnBankConfirmed(event.reference)
                    is StatusResult.Canceled -> TransferEvent.OnBankCanceled(event.reference)
                    is StatusResult.Unknown -> TransferEvent.OnBankStatusUnknown(status.reason)
                }
            } emits (
                "bank_confirmed" to TransferEvent.OnBankConfirmed::class,
                "bank_canceled" to TransferEvent.OnBankCanceled::class,
                "bank_status_unknown" to TransferEvent.OnBankStatusUnknown::class,
            )
        }
    }
}
```

For this modeled workflow, StateProof can generate tests with **100% path coverage of the modeled transaction state graph**. Integration, infrastructure, and visual correctness still require complementary test layers.
Canonical Mermaid sources:
- `docs/diagrams/screens-as-states.mmd`
- `docs/diagrams/runtime-event-processing.mmd`
- `docs/diagrams/bank-transaction-recovery.mmd`
- `docs/diagrams/generated-tests-simple-machine.mmd`

### Why This Works

- deterministic state graph instead of implicit UI flow
- exhaustive traversal of reachable modeled paths instead of ad-hoc test selection
- sync-safe test maintenance as transitions and guards evolve
- clear behavioral contract that complements TDD and manual exploratory testing
- AI-first onboarding with scan -> classify -> apply -> verify

## Generated Exhaustive Tests (Core Value)

### Simple Machine Example (Generates 6 Tests)

```mermaid
stateDiagram-v2
    [*] --> CheckoutStart
    CheckoutStart --> PaymentMethodSelection: OnCheckoutStarted
    PaymentMethodSelection --> CardAuth: OnPayByCard
    PaymentMethodSelection --> BankTransfer: OnPayByBank
    PaymentMethodSelection --> WalletPay: OnPayByWallet
    CardAuth --> Completed: OnCardApproved
    CardAuth --> Failed: OnCardDeclined
    BankTransfer --> Completed: OnBankApproved
    BankTransfer --> Failed: OnBankDeclined
    WalletPay --> Completed: OnWalletApproved
    WalletPay --> Failed: OnWalletDeclined
```

This graph has 3 payment branches x 2 outcomes, so StateProof deterministically generates exactly 6 terminal-path tests.

### Modeled State + Event Snippet

```kotlin
sealed class CheckoutState {
    object CheckoutStart : CheckoutState()
    object PaymentMethodSelection : CheckoutState()
    object CardAuth : CheckoutState()
    object BankTransfer : CheckoutState()
    object WalletPay : CheckoutState()
    object Completed : CheckoutState()
    object Failed : CheckoutState()
}

sealed class CheckoutEvent {
    object OnCheckoutStarted : CheckoutEvent()
    object OnPayByCard : CheckoutEvent()
    object OnPayByBank : CheckoutEvent()
    object OnPayByWallet : CheckoutEvent()
    object OnCardApproved : CheckoutEvent()
    object OnCardDeclined : CheckoutEvent()
    object OnBankApproved : CheckoutEvent()
    object OnBankDeclined : CheckoutEvent()
    object OnWalletApproved : CheckoutEvent()
    object OnWalletDeclined : CheckoutEvent()
}

val checkoutMachine = stateMachine<CheckoutState, CheckoutEvent>(CheckoutState.CheckoutStart) {
    // Deterministic event-to-transition mapping defines the traversal space.
    state<CheckoutState.CheckoutStart> {
        on<CheckoutEvent.OnCheckoutStarted> { transitionTo(CheckoutState.PaymentMethodSelection) }
    }

    state<CheckoutState.PaymentMethodSelection> {
        on<CheckoutEvent.OnPayByCard> { transitionTo(CheckoutState.CardAuth) }
        on<CheckoutEvent.OnPayByBank> { transitionTo(CheckoutState.BankTransfer) }
        on<CheckoutEvent.OnPayByWallet> { transitionTo(CheckoutState.WalletPay) }
    }

    state<CheckoutState.CardAuth> {
        on<CheckoutEvent.OnCardApproved> { transitionTo(CheckoutState.Completed) }
        on<CheckoutEvent.OnCardDeclined> { transitionTo(CheckoutState.Failed) }
    }

    state<CheckoutState.BankTransfer> {
        on<CheckoutEvent.OnBankApproved> { transitionTo(CheckoutState.Completed) }
        on<CheckoutEvent.OnBankDeclined> { transitionTo(CheckoutState.Failed) }
    }

    state<CheckoutState.WalletPay> {
        on<CheckoutEvent.OnWalletApproved> { transitionTo(CheckoutState.Completed) }
        on<CheckoutEvent.OnWalletDeclined> { transitionTo(CheckoutState.Failed) }
    }
}
```

### How Generated Tests Look

Example generated test names (hashes shown as examples):
- `_3_7A1C_from_CheckoutStart_to_Completed` (card approved path)
- `_3_84F1_from_CheckoutStart_to_Failed` (card declined path)
- `_3_1D2E_from_CheckoutStart_to_Completed` (bank approved path)
- `_3_55B0_from_CheckoutStart_to_Failed` (bank declined path)
- `_3_C8A9_from_CheckoutStart_to_Completed` (wallet approved path)
- `_3_90D4_from_CheckoutStart_to_Failed` (wallet declined path)

```kotlin
@StateProofGenerated(
    pathHash = "7A1C",
    generatedAt = "2026-02-13T12:00:00Z",
    schemaVersion = 1,
)
@Test
fun `_3_7A1C_from_CheckoutStart_to_Completed`() = runBlocking {
    //CheckoutStart_OnCheckoutStarted_PaymentMethodSelection_OnPayByCard_CardAuth_OnCardApproved_Completed

    // ▼▼▼ STATEPROOF:EXPECTED - Do not edit below this line ▼▼▼
    val expectedTransitions = listOf(
        "CheckoutStart_OnCheckoutStarted_PaymentMethodSelection",
        "PaymentMethodSelection_OnPayByCard_CardAuth",
        "CardAuth_OnCardApproved_Completed",
    )
    // ▲▲▲ STATEPROOF:END ▲▲▲

    // ══════════════════════════════════════════════════════════
    // User implementation below (preserved across regeneration)
    // ══════════════════════════════════════════════════════════

    // TODO: Implement test
}
```

### Why Your Test Code Stays Safe During Sync

- Sync updates only the generated expected-transition block.
- Sync preserves your implementation section below the markers.
- Removed traversal paths are marked obsolete, not deleted.
- User-written test code is never auto-deleted.

```kotlin
@StateProofObsolete(
    reason = "Path removed",
    markedAt = "2026-02-13T12:00:00Z",
    originalPath = "CheckoutStart -> PaymentMethodSelection -> CardAuth -> Failed",
)
@Ignore("StateProof: Path obsolete since 2026-02-13T12:00:00Z - review and delete manually")
```

### TransitionLog Verification of Traversal

```kotlin
val sm = createCheckoutStateMachine()

sm.onEvent(CheckoutEvent.OnCheckoutStarted)
sm.onEvent(CheckoutEvent.OnPayByCard)
sm.onEvent(CheckoutEvent.OnCardApproved)
sm.awaitIdle()

assertContentEquals(expectedTransitions, sm.getTransitionLog())
sm.close()
```

This verifies that runtime traversal exactly matches the generated expected path.

### Scope Statement

StateProof provides **100% path coverage of the modeled state graph**. Integration, infrastructure, and visual correctness still require complementary test layers.

## AI Agent Quickstart (3-Step)

1. Copy a skill into your agent:
   - Codex: `docs/ai/skills/codex/SKILL.md`
   - Claude: `docs/ai/skills/claude/SKILL.md`
2. Ask the agent to run setup in the current project:

   ```text
   Use the StateProof skill and run /stateproof setup for this project.
   Detect Gradle DSL first (build.gradle.kts vs build.gradle).
   Apply the correct plugin/dependency syntax without web search by using local StateProof docs.
   Then verify setup by running scan + sync + diagram + viewer tasks.
   ```

3. Run setup verification:

   ```bash
   ./gradlew :app:stateproofScan
   ./gradlew :app:stateproofSyncAll
   ./gradlew :app:stateproofDiagrams
   ./gradlew :app:stateproofViewer
   ```

If your module name is not `app`, replace `:app:` with your module path.

Full workflows, setup command contract, and playbooks: `docs/ai/README.md`.

## Definition of Done (Modeled Behavior)

Treat the generated suite as a done contract for modeled behavior when all checks below are true:

1. state/event model reflects the intended screen and flow behavior
2. guards and side-effect emitted events are explicitly represented
3. generated test set is synced and green
4. unresolved or unknown branches are reviewed and handled intentionally

This defines done for **modeled behavior** only, not full system quality.

## AI Agent Value (Constrained)

StateProof gives AI agents a concrete acceptance target: state graph + generated tests.

- if the model is correct, this yields near-exhaustive behavioral coverage for modeled paths
- with screen-as-state navigation modeling, the graph becomes a reviewable behavioral source of truth
- generated diagrams make behavior review easier than raw code-only diffs
- model quality is still the limiting factor; incomplete models can create false confidence

## Installation (Manual)

If you prefer manual setup without an AI agent, use the installation and Gradle setup sections below.

Kotlin DSL (`build.gradle.kts`):

```kotlin
plugins {
    id("io.stateproof") version "0.1.0-SNAPSHOT"
    id("com.google.devtools.ksp") // for auto-discovery
}

dependencies {
    implementation("io.stateproof:stateproof-core:0.1.0")
    implementation("io.stateproof:stateproof-annotations-jvm:0.1.0-SNAPSHOT")
    ksp("io.stateproof:stateproof-ksp:0.1.0-SNAPSHOT")
    testImplementation("io.stateproof:stateproof-viewer-jvm:0.1.0-SNAPSHOT")
}
```

Groovy DSL (`build.gradle`):

```groovy
plugins {
    id 'io.stateproof' version '0.1.0-SNAPSHOT'
    id 'com.google.devtools.ksp' // for auto-discovery
}

dependencies {
    implementation 'io.stateproof:stateproof-core:0.1.0'
    implementation 'io.stateproof:stateproof-annotations-jvm:0.1.0-SNAPSHOT'
    ksp 'io.stateproof:stateproof-ksp:0.1.0-SNAPSHOT'
    testImplementation 'io.stateproof:stateproof-viewer-jvm:0.1.0-SNAPSHOT'
}
```

Then configure `stateproof { ... }` in your module (examples in **Gradle Plugin** section).

## Quick Start

```kotlin
// Define your states
sealed class States {
    object Initial : States()
    object Loading : States()
    object Ready : States()
    object Error : States()
}

// Define your events
sealed class Events {
    object OnStart : Events()
    object OnSuccess : Events()
    object OnFailure : Events()
    object OnRetry : Events()
}

// Create a state machine
val stateMachine = stateMachine<States, Events>(States.Initial) {
    state<States.Initial> {
        on<Events.OnStart> { transitionTo(States.Loading) }
    }
    state<States.Loading> {
        on<Events.OnSuccess> { transitionTo(States.Ready) }
        on<Events.OnFailure> { transitionTo(States.Error) }
    }
    state<States.Error> {
        on<Events.OnRetry> { transitionTo(States.Loading) }
    }
}

// Generate exhaustive test cases
val testCases = stateMachine.enumerateAllPaths()
```

## JVM StateGraph Introspection

StateProof now provides a JVM-first rich graph model for introspection:

```kotlin
import io.stateproof.graph.toStateGraph

val graph = stateMachine.toStateGraph()
```

`StateGraph` contains:
- `states`: stable state nodes (`id`, `displayName`, `qualifiedName`, `groupId`, `isInitial`)
- `groups`: hierarchy groups inferred from sealed ancestry
- `transitions`: edge metadata (`guardLabel`, `emittedEvents`, and unresolved-target markers)

### Grouping Behavior

- If states are organized through nested sealed hierarchy, groups are auto-detected from that hierarchy.
- For flat state machines (no nested sealed path), all states are assigned to synthetic `General` (`group:General`).

### JVM Scope

`toStateGraph()` is currently JVM-first and intended for Android/JVM analysis workflows.
Existing `toStateInfo()` and sync/test generation APIs remain backward compatible.

## Static Diagrams (PlantUML + Mermaid/ELK)

Generate deterministic overview and per-group diagrams directly from `StateGraph`.

```kotlin
import io.stateproof.diagram.renderDiagrams
import io.stateproof.diagram.writeTo
import io.stateproof.graph.toStateGraph

val bundle = stateMachine.toStateGraph().renderDiagrams(machineName = "Main")
bundle.writeTo(File("build/stateproof/diagrams"))
```

Output layout:

```text
build/stateproof/diagrams/<machine>/
  overview.puml
  overview.mmd
  groups/
    <groupSlug>.puml
    <groupSlug>.mmd
```

Defaults:
- Mermaid output uses `flowchart LR` with ELK renderer directive.
- Edge labels include event + guard + emitted-event metadata.
- Overview shows aggregated inter-group edges (`count + sampled labels`).
- Per-group diagrams render cross-group transitions through `External::<...>` placeholders.

CLI:

```bash
# Single machine
stateproof diagrams \
  --provider com.example.MainStateMachineKt#createMainStateMachineForIntrospection \
  --is-factory \
  --output-dir build/stateproof/diagrams \
  --name main \
  --format both

# Auto-discovery (KSP registries)
stateproof diagrams-all \
  --output-dir build/stateproof/diagrams \
  --format both
```

## Interactive Viewer (MVP)

Generate a self-contained interactive HTML viewer per machine from `StateGraph`.

```kotlin
import io.stateproof.graph.toStateGraph
import io.stateproof.viewer.renderViewer
import io.stateproof.viewer.writeTo

val bundle = stateMachine.toStateGraph().renderViewer(machineName = "Main")
bundle.writeTo(File("build/stateproof/viewer"))
```

Output layout:

```text
build/stateproof/viewer/<machine>/
  index.html
  graph.json
```

Defaults:
- Cytoscape.js is bundled offline (no CDN needed).
- Default layout is `breadthfirst`.
- Viewer supports overview, group drill-down, state focus, search, and toolbar controls.
- `graph.json` sidecar is emitted by default (disable via CLI/Gradle option).

CLI (from `stateproof-viewer-jvm`):

```bash
# Single machine
stateproof-viewer viewer \
  --provider com.example.MainStateMachineKt#createMainStateMachineForIntrospection \
  --is-factory \
  --output-dir build/stateproof/viewer \
  --name main

# Auto-discovery (KSP registries)
stateproof-viewer viewer-all \
  --output-dir build/stateproof/viewer
```

## AI Agent Workflows (Detailed)

StateProof ships an AI-first integration kit so agents can onboard a project with a scan-first workflow.

Start here:

- AI docs index: `docs/ai/README.md`
- Codex skill: `docs/ai/skills/codex/SKILL.md`
- Claude skill: `docs/ai/skills/claude/SKILL.md`
- Migration playbooks: `docs/playbooks/`

### Install skill pack

Copy the skill file into your agent skill directory and invoke it in your prompt.

- Codex: use `docs/ai/skills/codex/SKILL.md`
- Claude: use `docs/ai/skills/claude/SKILL.md`

### Setup current project with AI

- Use command: `/stateproof setup`
- This setup flow should:
  - detect Gradle DSL (`build.gradle.kts` vs `build.gradle`)
  - apply plugin + dependency + config syntax for that DSL
  - use local StateProof docs (no internet search needed)
  - verify with `stateproofScan`, `stateproofSyncAll`, `stateproofDiagrams`, and `stateproofViewer`

### Existing Android/KMP migration (screens-as-states)

- Use playbook: `docs/playbooks/migration-existing-android-kmp.md`
- Run scan first:

```bash
./gradlew :app:stateproofScan
```

- Then run the generation workflow:

```bash
./gradlew :app:stateproofSyncAll
./gradlew :app:stateproofDiagrams
./gradlew :app:stateproofViewer
```

### Non-KMP fallback (state machine-only)

When your target project is JVM/non-KMP or not using screen-navigation mapping yet, use state-machine-only flow:

- Playbook: `docs/playbooks/non-kmp-state-machine-only.md`
- Sample: `samples/non-kmp-jvm/`

### `/stateproof` command reference (agent contract)

These command names are documented workflow aliases for AI agents:

- `/stateproof setup` -> setup plugin + dependencies + config for current project/module (Kotlin/Groovy DSL aware)
- `/stateproof scan` -> `stateproofScan`
- `/stateproof tests` -> `stateproofSyncAll`
- `/stateproof diagram` -> `stateproofDiagrams`
- `/stateproof viewer` -> `stateproofViewer`
- `/stateproof watch` -> `stateproofWatch`
- `/stateproof migrate-screens` -> migration playbook
- `/stateproof add-screen` -> add-screen playbook

### Scan + watch tasks

```bash
# Generate deterministic project profile JSON
./gradlew :app:stateproofScan

# Start watch loop (mode via stateproof.watchMode = tests|diagram|viewer|all)
./gradlew :app:stateproofWatch
```

## Gradle Plugin

StateProof uses **sync-only** to safely manage your test files.

### Important: Sync-Only Design

- ✅ **Adds** new tests for new state machine paths
- ✅ **Updates** expected transitions when paths change
- ✅ **Marks** obsolete tests with `@StateProofObsolete`
- ✅ **Preserves** all user-written test implementations
- ❌ **Never deletes** any test code automatically

**Test naming format**: `_depth_CRC_from_startState_to_endState`

Example: `_4_1698_from_Initial_to_Settings`

**To regenerate a test**: Delete it manually, then run sync again.

### Setup

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    id("io.stateproof") version "0.1.0-SNAPSHOT"
}

stateproof {
    stateMachines {
        create("main") {
            infoProvider.set("com.example.MainStateMachineKt#getMainStateMachineInfo")
            initialState.set("Initial")
        }
    }
}
```

If you use viewer tasks, add the viewer artifact to your test/runtime classpath:

```kotlin
dependencies {
    testImplementation("io.stateproof:stateproof-viewer-jvm:0.1.0-SNAPSHOT")
}
```

### Available Tasks

| Task | Description |
|------|-------------|
| `stateproofSyncAll` | Sync tests for all state machines |
| `stateproofSync<Name>` | Sync tests for a specific state machine |
| `stateproofSyncDryRun<Name>` | Preview sync changes without writing |
| `stateproofStatus<Name>` | Show current sync status |
| `stateproofCleanObsolete<Name>` | List obsolete tests |
| `stateproofDiagrams` | Generate static diagrams (single mode or alias to all) |
| `stateproofDiagrams<Name>` | Generate static diagrams for a specific state machine (multi mode) |
| `stateproofDiagramsAll` | Generate static diagrams for all state machines |
| `stateproofViewer` | Generate interactive viewer (single mode or alias to all) |
| `stateproofViewer<Name>` | Generate interactive viewer for a specific state machine (multi mode) |
| `stateproofViewerAll` | Generate interactive viewers for all state machines |
| `stateproofScan` | Generate project profile JSON for AI-assisted integration |
| `stateproofWatch` | Watch configured paths and trigger sync/diagram/viewer actions |

### Test Dependencies

Add these dependencies for generated tests:

```kotlin
// JVM / Android Unit Tests
testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.21")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

// Android Instrumented Tests (if using android target)
androidTestImplementation("org.jetbrains.kotlin:kotlin-test:1.9.21")
androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
```

## Roadmap (Planned)

These items are planned next and intentionally not treated as current guarantees:

- IDE plugins for IntelliJ IDEA and Android Studio guidance/automation
- screenshot test generation integration for visual regression confidence and story-like flow playback
- edge-case test generation integration for broader failure-path coverage

## Modules

- `stateproof-core` - Core KMP state machine library
- `stateproof-compose` - Compose Multiplatform integration (coming soon)
- `stateproof-navigation` - Jetpack Navigation integration (coming soon)
- `stateproof-gradle-plugin` - Gradle plugin for test, diagram, and viewer generation
- `stateproof-viewer` - Interactive state graph viewer generator (JVM-first)

## License

Apache 2.0
