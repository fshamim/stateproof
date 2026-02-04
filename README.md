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

## Modules

- `stateproof-core` - Core KMP state machine library
- `stateproof-compose` - Compose Multiplatform integration (coming soon)
- `stateproof-navigation` - Jetpack Navigation integration (coming soon)
- `stateproof-viewer` - Interactive state graph viewer (coming soon)

## License

Apache 2.0
