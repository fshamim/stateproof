# StateProof

A Kotlin Multiplatform state machine library with exhaustive test generation.

## Core Value Proposition

**The state graph IS your test suite.** StateProof's DFS traversal algorithm enumerates every valid path through your state machine, and each path becomes a test case. Your app's navigation, business logic, and error flows are all provably covered just by defining the state machine.

This turns the traditional testing model on its head:
- **Traditional**: Write app logic -> manually write tests -> hope you covered everything
- **StateProof**: Define app as state machine -> tests are generated exhaustively -> coverage is mathematically complete

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.stateproof:stateproof-core:0.1.0")
}
```

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

### Available Tasks

| Task | Description |
|------|-------------|
| `stateproofSyncAll` | Sync tests for all state machines |
| `stateproofSync<Name>` | Sync tests for a specific state machine |
| `stateproofSyncDryRun<Name>` | Preview sync changes without writing |
| `stateproofStatus<Name>` | Show current sync status |
| `stateproofCleanObsolete<Name>` | List obsolete tests |

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

## Modules

- `stateproof-core` - Core KMP state machine library
- `stateproof-compose` - Compose Multiplatform integration (coming soon)
- `stateproof-navigation` - Jetpack Navigation integration (coming soon)
- `stateproof-gradle-plugin` - Gradle plugin for test generation
- `stateproof-viewer` - Interactive state graph viewer (coming soon)

## License

Apache 2.0
