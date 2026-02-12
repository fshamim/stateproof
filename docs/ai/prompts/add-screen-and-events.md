# Prompt Template: Add Screen + Events + Back Handling

```text
Add a new screen flow using StateProof screens-as-states pattern.

Steps:
1) Identify target state machine and current adjacent states.
2) Add the new screen state and related events.
3) Add transitions with explicit guard conditions for data-dependent branches.
4) Model back handling as an explicit event transition.
5) If side effects can emit events, declare emitted event metadata in the DSL branch.
6) Update navigation mapping for the new state.
7) Run:
   - ./gradlew :app:stateproofSyncAll
   - ./gradlew :app:stateproofDiagrams
   - ./gradlew :app:stateproofViewer
8) Confirm generated tests include the new paths.

Output:
- changed files
- generated tests/diagrams/viewer paths
- any ambiguous transitions that need product decisions
```
