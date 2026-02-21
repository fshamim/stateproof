# StateProof Integration Skill (Claude)

## Goal

Provide a predictable StateProof integration flow with compile-safe checkpoints.

## Required architecture for Android/KMP screen flows

Use `docs/SCREENS_AS_STATES.md` as mandatory design contract:

1. route-only states
2. reactive UI/business state in `MutableStateFlow` container
3. shared KMP ViewModel-like facade (`state`, `stateData`, `onEvents`)
4. repository-driven side effects
5. `doNotTransition()` for in-place updates

## Required sequence

1. run scan (`stateproofScan`)
2. read project profile JSON
3. choose mode (`SCREENS_AS_STATES` or `STATE_MACHINE_ONLY`)
4. propose short implementation plan
5. apply changes in small slices
6. verify sync/diagram/viewer tasks

## Generated test guidance

1. Keep `expectedTransitions` unchanged except via sync.
2. Executable generated bodies should remain executable.
3. For tests marked `STATEPROOF:MANUAL_REQUIRED`, implement through shared helper/harness.
4. Avoid bespoke per-test setup when one reusable helper can handle the same pattern.

## KMP test target policy

Default:

1. generated path tests -> `desktopTest` (or configured JVM test dir)
2. pure logic tests -> `commonTest`
3. screenshot baseline -> `desktopTest`
4. optional Android screenshot parity -> `androidTest`

## Canonical setup coordinates

- Plugin: `id("io.github.fshamim.stateproof") version "0.8.0-alpha02"`
- Core: `implementation("io.github.fshamim:stateproof-core-jvm:0.8.0-alpha02")`
- Annotations: `implementation("io.github.fshamim:stateproof-annotations:0.8.0-alpha02")`
- KSP: `ksp("io.github.fshamim:stateproof-ksp:0.8.0-alpha02")`
- Viewer (test): `testImplementation("io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha02")`
- Android navigation: `implementation("io.github.fshamim:stateproof-navigation:0.8.0-alpha02")` when needed

Do not use `io.stateproof:*` Maven coordinates.

## `/stateproof` aliases

- `/stateproof setup` -> setup current module (DSL aware, local docs first)
- `/stateproof scan` -> `./gradlew <module>:stateproofScan`
- `/stateproof tests` -> `./gradlew <module>:stateproofSyncAll`
- `/stateproof diagram` -> `./gradlew <module>:stateproofDiagrams`
- `/stateproof viewer` -> `./gradlew <module>:stateproofViewer`
- `/stateproof watch` -> `./gradlew <module>:stateproofWatch`
- `/stateproof migrate-screens` -> migration playbook
- `/stateproof add-screen` -> add-screen playbook

## Editing rules

- detect Kotlin/Groovy Gradle DSL before edits
- use factory/KSP auto-discovery when possible
- keep compile-safe changes first
- preserve existing user test implementations
- keep guarded branches explicit
