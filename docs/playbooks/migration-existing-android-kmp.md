# Playbook: Existing Android/KMP Migration (Screens-as-States)

## Objective

Adopt StateProof with the canonical KMP architecture from `docs/SCREENS_AS_STATES.md` without rewriting the full app in one step.

## Step-by-step

1. Apply plugin and dependencies
   - plugin: `io.github.fshamim.stateproof` (`0.8.0-alpha02`)
   - `io.github.fshamim:stateproof-core-jvm:0.8.0-alpha02`
   - `io.github.fshamim:stateproof-navigation:0.8.0-alpha02` (Android nav mapping)
   - `io.github.fshamim:stateproof-annotations:0.8.0-alpha02`
   - `io.github.fshamim:stateproof-ksp:0.8.0-alpha02`
   - `io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha02` (test/viewer workflows)
2. Run scan
   - `./gradlew :app:stateproofScan`
   - confirm `integrationMode = SCREENS_AS_STATES`
3. Split route state and UI data
   - convert route states to route-only objects
   - move screen/business data to `MutableStateFlow` UI-state container
4. Introduce shared KMP ViewModel-style routing facade
   - expose `state: StateFlow<RouteState>`
   - expose read-only `stateData`
   - route all UI interaction through `onEvents(...)`
5. Refactor machine side effects to repository boundary
   - no direct DB/framework calls in state machine
   - mutate state-data flows via side effects
6. Model navigation behavior explicitly
   - explicit back events
   - explicit guard branches
   - explicit emitted-event metadata for side-effect follow-up events
7. Use `doNotTransition()` for in-place content updates
   - toggle/delete/list refresh paths that should not trigger route animation
8. Configure generated test target directory for KMP
   - prefer JVM/desktop target (e.g. `src/desktopTest/kotlin/...`)
   - keep `commonTest` for pure logic tests
9. Generate artifacts
   - `stateproofSyncAll`, `stateproofDiagrams`, `stateproofViewer`
10. Validate and complete generated tests
   - treat `expectedTransitions` as authoritative
   - implement tests flagged with `STATEPROOF:MANUAL_REQUIRED` via shared harness helpers

## Quality gates

1. Route-state classes are payload-free.
2. All UI interactions pass via `onEvents(...)`.
3. Side effects depend on repositories, not platform/data-layer concretes.
4. Generated tests sync cleanly; manual-required markers are resolved intentionally.
5. KMP test target policy documented in module README (`desktopTest` default, optional Android parity).

## Rollback

- Keep migration in isolated commits per state machine/module.
- Revert only migration-related commits when behavior mismatch is detected.
