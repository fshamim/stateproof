# StateProof AI Agent Kit (Codex + Claude)

This folder provides a practical skill pack and playbooks so an AI agent can integrate StateProof with minimal manual work.

## What this kit solves

- Detects project shape first (`/stateproof scan`)
- Chooses integration path automatically:
  - `SCREENS_AS_STATES` for Android + Compose + navigation
  - `STATE_MACHINE_ONLY` for JVM/non-KMP or non-navigation flows
- Applies changes with checkpoints:
  1. scan
  2. classify
  3. plan
  4. apply
  5. verify

## Canonical coordinates (must use)

Use these exact coordinates during AI setup/integration:

- Plugin ID: `io.github.fshamim.stateproof`
- Version: `0.8.0-alpha01`
- Core: `io.github.fshamim:stateproof-core-jvm:0.8.0-alpha01`
- Annotations: `io.github.fshamim:stateproof-annotations:0.8.0-alpha01`
- KSP processor: `io.github.fshamim:stateproof-ksp:0.8.0-alpha01`
- Viewer (test): `io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha01`
- Navigation (Android screens-as-states): `io.github.fshamim:stateproof-navigation:0.8.0-alpha01`

Do not use `io.stateproof:*` Maven coordinates. Source package names remain `io.stateproof.*`.

## Skill files

- Codex: `docs/ai/skills/codex/SKILL.md`
- Claude: `docs/ai/skills/claude/SKILL.md`

Copy the skill file into your agent skill location, then invoke it in your prompt.

## `/stateproof` command contract (agent workflow)

These are **workflow commands** for AI agents. They map to Gradle tasks and documented steps.

### `/stateproof setup`

- Pre-checks:
  - detect Gradle DSL first (`build.gradle.kts` vs `build.gradle`)
  - detect module target (default `:app`, fallback to discovered module)
- Action:
  - apply plugin + dependency + baseline `stateproof { ... }` config using DSL-correct syntax
  - use canonical coordinates:
    - plugin `io.github.fshamim.stateproof` (`0.8.0-alpha01`)
    - `io.github.fshamim:stateproof-core-jvm:0.8.0-alpha01`
    - `io.github.fshamim:stateproof-annotations:0.8.0-alpha01`
    - `io.github.fshamim:stateproof-ksp:0.8.0-alpha01`
    - `io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha01` (test)
  - use local StateProof docs only (no internet search required)
- Verify:
  - run `stateproofScan`, `stateproofSyncAll`, `stateproofDiagrams`, `stateproofViewer`
- Failure handling:
  - if scan task is missing, fix plugin/config wiring and rerun setup
- Rollback:
  - revert only setup-related Gradle/doc edits

### `/stateproof scan`

- Pre-checks:
  - plugin `io.github.fshamim.stateproof` applied
- Action:
  - run `./gradlew <module>:stateproofScan`
- Verify:
  - report exists at `build/stateproof/agent/project-scan.json`
- Failure handling:
  - if task missing, add plugin and retry
- Rollback:
  - none (read-only)

### `/stateproof tests`

- Pre-checks:
  - provider/factory config or KSP auto-discovery in place
- Action:
  - run `./gradlew <module>:stateproofSyncAll`
- Verify:
  - generated/updated tests in configured test directories
- Failure handling:
  - run compile/KSP tasks once, then retry
- Rollback:
  - revert generated files if integration path was wrong

### `/stateproof diagram`

- Action: `./gradlew <module>:stateproofDiagrams`
- Verify: files under `build/stateproof/diagrams`

### `/stateproof viewer`

- Action: `./gradlew <module>:stateproofViewer`
- Verify: files under `build/stateproof/viewer`

### `/stateproof watch`

- Action: `./gradlew <module>:stateproofWatch`
- Modes via extension `watchMode`: `tests|diagram|viewer|all`
- Verify: one change in watch paths triggers expected action set

### `/stateproof migrate-screens`

- Use playbook: `docs/playbooks/migration-existing-android-kmp.md`
- Expected result:
  - screens mapped to states
  - events mapped to user actions + side effects
  - back handling modeled as explicit event transitions

### `/stateproof add-screen`

- Use playbook: `docs/playbooks/add-screen-event-back.md`
- Expected result:
  - new state + event classes
  - transitions + guarded side effects
  - navigation/back mapping updates

## Prompt templates

- `docs/ai/prompts/setup-current-project.md`
- `docs/ai/prompts/integrate-existing-android-kmp.md`
- `docs/ai/prompts/integrate-existing-non-kmp.md`
- `docs/ai/prompts/add-screen-and-events.md`

## Replay references

- Non-KMP fallback replay: `docs/ai/replays/non-kmp-sample-replay.md`
- Consumer-specific integration replays should stay in the consumer repository.
