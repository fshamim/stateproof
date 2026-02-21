# Playbook: Non-KMP / JVM State Machine-Only Integration

## When to use

- JVM service or library projects
- Android projects without screens-as-states migration yet
- teams adopting StateProof for logic verification first

## Steps

1. Add plugin + core dependencies
   - plugin: `io.github.fshamim.stateproof` (`0.8.0-alpha02`)
   - `io.github.fshamim:stateproof-core-jvm:0.8.0-alpha02`
   - `io.github.fshamim:stateproof-annotations:0.8.0-alpha02`
   - `io.github.fshamim:stateproof-ksp:0.8.0-alpha02`
   - `io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha02` (test)
2. Configure one provider/factory (or multiple)
3. Set initial state and test output settings
4. Run `stateproofSyncAll`
5. Generate `stateproofDiagrams`
6. Generate `stateproofViewer`
7. Add generated tests to CI

## Non-goals in this flow

- no navigation mapping
- no UI screen migration required

## Expected outputs

- exhaustive path-based tests for configured state machines
- deterministic diagrams and interactive viewer assets
