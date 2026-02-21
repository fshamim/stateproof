# StateProof `0.8.0-alpha02` Announcement Draft

StateProof `0.8.0-alpha02` is now available as a Maven Central prerelease.

## What this release focuses on

- Stable core sync flow for generated state-machine tests
- JVM `StateGraph` introspection
- Static diagrams (PlantUML + Mermaid)
- Interactive viewer generation (offline HTML + JSON)
- AI-first onboarding docs and workflows

## Install

```kotlin
plugins {
    id("io.github.fshamim.stateproof") version "0.8.0-alpha02"
}

dependencies {
    implementation("io.github.fshamim:stateproof-core-jvm:0.8.0-alpha02")
    implementation("io.github.fshamim:stateproof-annotations:0.8.0-alpha02")
    ksp("io.github.fshamim:stateproof-ksp:0.8.0-alpha02")
    testImplementation("io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha02")
}
```

## Alpha scope

`0.8.0-alpha02` is a prerelease intended to validate integration and workflow stability before a 1.0 launch.

## Feedback channels

- GitHub Issues
- GitHub Discussions
