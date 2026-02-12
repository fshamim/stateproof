# StateProof Integration Skill (Codex)

## Purpose

Integrate StateProof into an existing or new Kotlin project with deterministic checkpoints and verification.

## Trigger

Use this skill when the user asks to:

- integrate StateProof
- setup StateProof in the current project
- migrate to screens-as-states
- add a new screen/event flow with StateProof
- run StateProof scan/tests/diagram/viewer/watch workflows

## Workflow (must follow in order)

1. **Scan**
   - Run `./gradlew <module>:stateproofScan`
   - Read `build/stateproof/agent/project-scan.json`
2. **Classify**
   - If report says `SCREENS_AS_STATES`: choose Android/KMP migration flow
   - Else choose `STATE_MACHINE_ONLY` flow
3. **Plan**
   - List minimal file edits and Gradle tasks
4. **Apply**
   - Make changes incrementally
   - Keep existing architecture intact unless requested
5. **Verify**
   - Run:
     - `stateproofSyncAll`
     - `stateproofDiagrams`
     - `stateproofViewer`
   - Confirm generated outputs are non-empty

## Command contract

- `/stateproof setup` -> apply plugin + dependencies + baseline config for current project/module (detect `build.gradle.kts` vs `build.gradle` first; use local docs, no web search)
- `/stateproof scan` -> `stateproofScan`
- `/stateproof tests` -> `stateproofSyncAll`
- `/stateproof diagram` -> `stateproofDiagrams`
- `/stateproof viewer` -> `stateproofViewer`
- `/stateproof watch` -> `stateproofWatch`
- `/stateproof migrate-screens` -> follow `docs/playbooks/migration-existing-android-kmp.md`
- `/stateproof add-screen` -> follow `docs/playbooks/add-screen-event-back.md`

## Guardrails

- Always detect Gradle DSL before editing (`.kts` vs Groovy) and emit matching syntax.
- For setup flow, prefer local repository docs/context over internet search.
- Prefer factory-based introspection (`factory`/`stateMachineFactoryFqn`) over manual maps
- Keep transition intent explicit (including guarded branches and side-effect emitted events)
- Preserve user test implementations; rely on sync-only updates
- For non-KMP/JVM apps, do not force navigation integration

## Verification checklist

- `stateproofScan` report present and coherent
- tests/diagram/viewer tasks succeed
- no unrelated file rewrites
- generated artifacts appear under `build/stateproof/*`
