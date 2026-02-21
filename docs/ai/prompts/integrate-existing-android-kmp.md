# Prompt Template: Integrate StateProof into Existing Android/KMP App

Use this prompt with an AI coding agent:

```text
You are integrating StateProof into this Android/KMP project.

Follow this strict flow:
1) Run `./gradlew :app:stateproofScan` and read `app/build/stateproof/agent/project-scan.json`.
2) Classify integration mode from the report.
3) If mode is SCREENS_AS_STATES, implement architecture from docs/SCREENS_AS_STATES.md:
   - route-only AppState (no data payload in route states)
   - reactive UI data container with MutableStateFlow fields
   - shared KMP ViewModel-like facade exposing `state`, `stateData`, and `onEvents(...)`
   - repository-only side effects
   - use doNotTransition() for in-place content updates
4) Keep production behavior unchanged while adding introspection/test generation wiring.
5) Run and verify:
   - ./gradlew :app:stateproofSyncAll
   - ./gradlew :app:stateproofDiagrams
   - ./gradlew :app:stateproofViewer
6) Report changed files, generated output paths, and assumptions.

Generated test rules:
- Treat expectedTransitions as authoritative.
- If generated test includes STATEPROOF:MANUAL_REQUIRED, implement via shared helper/harness.
- Preserve generated marker blocks and annotation metadata.

KMP test-target guidance:
- generated path tests and screenshot baseline -> desktopTest/JVM target
- pure logic tests -> commonTest
- optional Android visual parity -> androidTest

Constraints:
- Use plugin `io.github.fshamim.stateproof` and dependencies from `io.github.fshamim` group only.
- Keep version pinned to `0.8.0-alpha02` for all StateProof modules.
- Prefer factory/KSP auto-discovery; avoid manual state transition maps.
- Preserve existing test implementations.
- Keep guarded transitions explicit and declarative.
```
