# Prompt Template: Integrate StateProof into Existing Non-KMP/JVM Project

```text
Integrate StateProof in STATE_MACHINE_ONLY mode.

Do this in order:
1) Run `./gradlew stateproofScan` and inspect `build/stateproof/agent/project-scan.json`.
2) Add only required dependencies for non-KMP flow (no navigation/screen mapping).
   Use:
   - plugin `io.github.fshamim.stateproof` version `0.8.0-alpha01`
   - `io.github.fshamim:stateproof-core-jvm:0.8.0-alpha01`
   - `io.github.fshamim:stateproof-annotations:0.8.0-alpha01`
   - `io.github.fshamim:stateproof-ksp:0.8.0-alpha01`
   - `io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha01` (test)
3) Configure stateproof plugin with explicit provider/factory or auto-discovery.
4) Run and verify:
   - ./gradlew stateproofSyncAll
   - ./gradlew stateproofDiagrams
   - ./gradlew stateproofViewer
5) Summarize generated outputs and next migration opportunities.

Rules:
- Do not add Android/Compose navigation code.
- Keep the state machine deterministic; model guarded branches explicitly.
```
