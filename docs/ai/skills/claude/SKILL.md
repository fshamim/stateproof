# StateProof Integration Skill (Claude)

## Goal

Provide a predictable integration workflow for StateProof with minimal manual edits.

## Required sequence

1. Run project scan (`stateproofScan`)
2. Read project profile JSON
3. Choose one integration mode:
   - `SCREENS_AS_STATES` (Android/Compose/navigation)
   - `STATE_MACHINE_ONLY` (JVM/non-KMP fallback)
4. Propose a short implementation plan
5. Apply changes in small slices
6. Verify with sync/diagram/viewer tasks

## `/stateproof` workflow aliases

- `/stateproof setup` -> setup StateProof in the current project/module (detect `build.gradle.kts` vs `build.gradle` first; use local docs, no web search)
- `/stateproof scan` -> run `./gradlew <module>:stateproofScan`
- `/stateproof tests` -> run `./gradlew <module>:stateproofSyncAll`
- `/stateproof diagram` -> run `./gradlew <module>:stateproofDiagrams`
- `/stateproof viewer` -> run `./gradlew <module>:stateproofViewer`
- `/stateproof watch` -> run `./gradlew <module>:stateproofWatch`
- `/stateproof migrate-screens` -> use migration playbook
- `/stateproof add-screen` -> use add-screen playbook

## Mode selection rules

- If Android + Compose + Nav are present, pick `SCREENS_AS_STATES`
- Otherwise pick `STATE_MACHINE_ONLY`
- Never inject navigation scaffolding into non-navigation JVM apps

## Editing rules

- Always detect Gradle DSL before edits and apply matching Kotlin/Groovy syntax.
- For setup flow, use local StateProof docs/context; internet lookup is unnecessary.
- Keep compile-safe changes first (dependencies + plugin + config)
- Add state machine definitions and introspection factories next
- Generate/sync tests last
- If generation differs from expectation, trust state graph extraction and inspect guarded branches

## Completion criteria

- scan report generated
- sync/diagram/viewer tasks all pass
- docs/playbook links added where relevant
