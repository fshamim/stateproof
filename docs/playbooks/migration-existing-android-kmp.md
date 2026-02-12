# Playbook: Existing Android/KMP Migration (Screens-as-States)

## Objective

Adopt StateProof without rewriting app architecture in one step.

## Step-by-step

1. Apply plugin and dependencies
   - `io.stateproof` plugin
   - `stateproof-core-jvm`, `stateproof-navigation`, `stateproof-annotations-jvm`, `stateproof-ksp`
2. Run scan
   - `./gradlew :app:stateproofScan`
   - confirm `integrationMode = SCREENS_AS_STATES`
3. Annotate introspection factories
   - ensure factory creates state machine with deterministic test-safe defaults
4. Configure plugin
   - prefer auto-discovery (KSP registries)
   - fallback: explicit `stateMachines { ... }`
5. Model back handling explicitly
   - use explicit back events and transitions
6. Model data-dependent paths
   - guarded branches with explicit conditions
   - side-effect emitted events declared in branch metadata
7. Generate artifacts
   - `stateproofSyncAll`, `stateproofDiagrams`, `stateproofViewer`
8. Validate
   - generated tests reflect expected flows
   - no production behavior regressions

## Rollback

- Keep migration in isolated commits per state machine
- revert only migration commit(s) if behavioral mismatch is detected
