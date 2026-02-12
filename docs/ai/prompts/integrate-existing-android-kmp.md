# Prompt Template: Integrate StateProof into Existing Android/KMP App

Use this prompt with an AI coding agent:

```text
You are integrating StateProof into this Android/KMP project.

Follow this strict flow:
1) Run `./gradlew :app:stateproofScan` and read `app/build/stateproof/agent/project-scan.json`.
2) Classify integration mode from the report.
3) If mode is SCREENS_AS_STATES, apply screens-as-states migration incrementally.
4) Keep production behavior unchanged while adding introspection/test generation wiring.
5) Run and verify:
   - ./gradlew :app:stateproofSyncAll
   - ./gradlew :app:stateproofDiagrams
   - ./gradlew :app:stateproofViewer
6) Report file changes, generated output paths, and any assumptions.

Constraints:
- Prefer factory/KSP auto-discovery; avoid manual state transition maps.
- Preserve existing test implementations.
- Use guarded transitions for data-dependent branches.
```
