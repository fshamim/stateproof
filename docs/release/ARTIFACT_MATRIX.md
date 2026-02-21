# Artifact Matrix (`0.8.0-alpha02`)

This matrix defines the OSS artifact set targeted in Sprint 14.

## Core OSS Modules

| Module | Primary published artifacts |
|---|---|
| `:stateproof-core` | `io.github.fshamim:stateproof-core`, `io.github.fshamim:stateproof-core-jvm` |
| `:stateproof-annotations` | `io.github.fshamim:stateproof-annotations` |
| `:stateproof-ksp` | `io.github.fshamim:stateproof-ksp` |
| `:stateproof-compose` | `io.github.fshamim:stateproof-compose`, `io.github.fshamim:stateproof-compose-jvm` |
| `:stateproof-navigation` | `io.github.fshamim:stateproof-navigation` |
| `:stateproof-viewer` | `io.github.fshamim:stateproof-viewer`, `io.github.fshamim:stateproof-viewer-jvm` |
| `:stateproof-gradle-plugin` | `io.github.fshamim:stateproof-gradle-plugin`, plugin marker publication(s) for `id(\"io.github.fshamim.stateproof\")` |

## Notes

- Kotlin Multiplatform modules publish root metadata plus platform-specific artifacts.
- JVM consumers should use `*-jvm` coordinates where applicable.
- Gradle plugin consumption remains via plugin ID:

```kotlin
plugins {
    id("io.github.fshamim.stateproof") version "0.8.0-alpha02"
}
```
