# Prompt Template: Setup StateProof in Current Project

```text
Use the StateProof skill and run /stateproof setup for this current project.

Requirements:
1) Detect Gradle DSL first:
   - Kotlin DSL: build.gradle.kts / settings.gradle.kts
   - Groovy DSL: build.gradle / settings.gradle
2) Apply plugin + dependencies + baseline stateproof configuration using the correct DSL syntax.
   Use exactly:
   - plugin: io.github.fshamim.stateproof (version 0.8.0-alpha01)
   - io.github.fshamim:stateproof-core-jvm:0.8.0-alpha01
   - io.github.fshamim:stateproof-annotations:0.8.0-alpha01
   - io.github.fshamim:stateproof-ksp:0.8.0-alpha01
   - io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha01 (test)
3) Do not browse the internet for setup details; use local StateProof docs in this repo.
4) Verify setup by running:
   - ./gradlew <module>:stateproofScan
   - ./gradlew <module>:stateproofSyncAll
   - ./gradlew <module>:stateproofDiagrams
   - ./gradlew <module>:stateproofViewer
5) Report:
   - detected module + DSL
   - changed files
   - verification command results
   - any assumptions
```
