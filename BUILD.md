# Building StateProof

## Prerequisites
- JDK 17+
- Android SDK (for stateproof-navigation)

## Modules
- `stateproof-core` - KMP state machine library
- `stateproof-navigation` - Android Jetpack Navigation integration

## Build & Publish to Maven Local

### All modules
```bash
./gradlew publishToMavenLocal
```

### Individual modules
```bash
# Core only
./gradlew :stateproof-core:publishToMavenLocal

# Navigation only
./gradlew :stateproof-navigation:publishToMavenLocal
```

## Run Tests

```bash
# All tests
./gradlew test

# Core tests
./gradlew :stateproof-core:allTests

# Navigation tests
./gradlew :stateproof-navigation:test
```

## Clean Build

```bash
./gradlew clean publishToMavenLocal
```

## Using in Your Project

Add to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.fshamim:stateproof-core-jvm:0.8.0-alpha02")
    implementation("io.github.fshamim:stateproof-navigation:0.8.0-alpha02")
}
```

## Refresh in Consuming Project

After publishing a new version, clear the Gradle cache to force the consuming project to use the latest version:
```bash
rm -rf ~/.gradle/caches/modules-2/files-2.1/io.github.fshamim
```

Or combine publish + cache clear in one command:
```bash
./gradlew publishToMavenLocal && rm -rf ~/.gradle/caches/modules-2/files-2.1/io.github.fshamim
```

Then rebuild your consuming project (e.g., `./gradlew assembleDebug`).

## Verify Publication

Check Maven Local (~/.m2/repository/io/github/fshamim/):
```bash
ls ~/.m2/repository/io/github/fshamim/
```

For Maven Central release flow, see: `docs/release/MAVEN_CENTRAL_RELEASE.md`.
