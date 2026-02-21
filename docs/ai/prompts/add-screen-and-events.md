# Prompt Template: Add Screen + Events + Back Handling

```text
Add a new screen flow using the StateProof KMP screens-as-states architecture.

Follow this sequence:
1) Identify target state machine and neighboring route states.
2) Add route-only state object(s) (no payload fields in route state).
3) Add/extend view events:
   - AppEvent (machine-routed)
   - LocalUiEvent (view-model local behavior when needed)
4) Add transitions with explicit guard conditions for data-dependent branches.
5) If side effects emit events, declare emitted-event metadata in transition branch.
6) Keep business/UI data in reactive state-data container (MutableStateFlow), not in AppState.
7) Use repository-only side effects; avoid direct DB/framework calls in machine.
8) For in-place updates (list/detail toggles etc), use doNotTransition().
9) Update UI mapping and route fallback behavior (e.g., stale selected item handling).
10) Run:
   - ./gradlew :app:stateproofSyncAll
   - ./gradlew :app:stateproofDiagrams
   - ./gradlew :app:stateproofViewer
11) Confirm generated tests include new paths.
12) If generated tests contain STATEPROOF:MANUAL_REQUIRED, implement shared harness helpers instead of per-test ad-hoc edits.

Output:
- changed files
- generated tests/diagrams/viewer paths
- manual-required test markers and chosen helper strategy
- any ambiguous transitions requiring product decisions
```
