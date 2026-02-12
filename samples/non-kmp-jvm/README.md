# Non-KMP JVM Sample

This sample demonstrates the **STATE_MACHINE_ONLY** fallback path.

## Run

```bash
./gradlew stateproofScan
./gradlew stateproofSyncAll
./gradlew stateproofDiagrams
./gradlew stateproofViewer
```

## Expected output

- Scan report: `build/stateproof/agent/project-scan.json`
- Synced tests: `src/test/kotlin/generated/stateproof`
- Diagrams: `build/stateproof/diagrams`
- Viewer: `build/stateproof/viewer`
