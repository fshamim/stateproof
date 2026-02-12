# Replay: Non-KMP JVM Sample

## Context

Project type: JVM-only sample under `samples/non-kmp-jvm`.

## Replay

1. `./gradlew stateproofScan`
2. Report classified project as `JVM` + `STATE_MACHINE_ONLY`
3. Ran:
   - `./gradlew stateproofSyncAll`
   - `./gradlew stateproofDiagrams`
   - `./gradlew stateproofViewer`
4. Verified outputs:
   - tests generated under `src/test/kotlin/generated/...`
   - diagrams under `build/stateproof/diagrams`
   - viewer under `build/stateproof/viewer`

## Notes

- No navigation integration is required in fallback mode.
- Same test/diagram/viewer workflow still applies.
