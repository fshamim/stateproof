# Tasks

## Completed: KMP/CMP StateProof Sample App Plan (Steelman Debate)

**Status**: Plan converged (severity 2/10 after 3 rounds)
**Date**: 2026-02-16

### Debate Evolution
- Round 1 (v1 -> severity 7): Strong architecture but speculated APIs, no discovery phase, no fallbacks for "coming soon" modules
- Round 2 (v2 -> severity 4): Added Phase 0 discovery, source citations, fallback paths, error recovery
- Round 3 (v3 -> severity 2): API verification, substitution variables, androidApp module, Gradle wrapper, KSP transparency, Compose BOM fix, desktop run task

---

# Final Plan: KMP/CMP StateProof Sample App ("TaskProof")

## Summary

A task manager app demonstrating ALL 7 StateProof modules across Android + iOS + Desktop using the "screens-as-states" pattern with guards, side effects, back handling, data-carrying states, exhaustive test generation, and interactive viewer.

## Phase 0: Discovery & Verification

### Step 0.1: Clone or verify repo access
```bash
git clone https://github.com/fshamim/stateproof /tmp/stateproof-ref
# OR verify we're in the repo: ls settings.gradle.kts
```

### Step 0.2: Check Maven Central for artifacts
```bash
curl -s "https://repo1.maven.org/maven2/io/github/fshamim/stateproof-core-jvm/0.8.0-alpha01/" | head -20
curl -s "https://repo1.maven.org/maven2/io/github/fshamim/stateproof-compose/0.8.0-alpha01/" | head -20
curl -s "https://repo1.maven.org/maven2/io/github/fshamim/stateproof-navigation/0.8.0-alpha01/" | head -20
curl -s "https://repo1.maven.org/maven2/io/github/fshamim/stateproof-viewer-jvm/0.8.0-alpha01/" | head -20
```
Decision: If found -> `mavenCentral()`. If not -> Step 0.3.

### Step 0.3 (conditional): Publish to mavenLocal
```bash
cd /path/to/stateproof && ./gradlew publishToMavenLocal
```
Verify: `ls ~/.m2/repository/io/github/fshamim/stateproof-core-jvm/0.8.0-alpha01/`
Error recovery: signing issues -> add `signing.required=false` to gradle.properties

### Step 0.4: Verify artifact coordinates
```bash
ls ~/.m2/repository/io/github/fshamim/ | sort
```

### Step 0.5: Read AI-friendly docs
- `docs/ai/README.md`, `docs/ai/skills/claude/SKILL.md`, `docs/SCREENS_AS_STATES.md`, `docs/playbooks/add-screen-event-back.md`
- Fallback if paths changed: `find docs/ -name '*.md' | sort`

### Step 0.6: Read existing sample (`samples/non-kmp-jvm/`)

### Step 0.7: Verify StateMachine public API
Read `stateproof-core/src/commonMain/kotlin/io/stateproof/StateMachine.kt`. Confirm:
- `onEvent(event: EVENT)` — fire-and-forget dispatch
- `val currentState: STATE` — synchronous state
- `val state: StateFlow<STATE>` — observable flow
- `suspend fun awaitIdle()` — wait for queue drain
- `fun close()` — cancel scope
- `fun getTransitionLog(): List<String>` — recorded transitions (if missing, search alternatives)

### Step 0.8: Produce substitution variables
```
CORE_ARTIFACT = "io.github.fshamim:stateproof-core:0.8.0-alpha01"
COMPOSE_ARTIFACT = "io.github.fshamim:stateproof-compose:0.8.0-alpha01"
NAVIGATION_ARTIFACT = "io.github.fshamim:stateproof-navigation:0.8.0-alpha01"
ANNOTATIONS_ARTIFACT = "io.github.fshamim:stateproof-annotations:0.8.0-alpha01"
VIEWER_ARTIFACT = "io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha01"
COMPOSE_AVAILABLE = true/false
NAVIGATION_AVAILABLE = true/false
SM_AWAIT_IDLE = "awaitIdle()"
SM_TRANSITION_LOG = "getTransitionLog()"
SM_CLOSE = "close()"
```

---

## Phase 1: Project Scaffolding

### Step 1.1: Directory structure
```bash
mkdir -p samples/TaskAppKMP/{composeApp,androidApp}/src
mkdir -p samples/TaskAppKMP/composeApp/src/{commonMain,commonTest,androidMain,iosMain,desktopMain,desktopTest}/kotlin/io/stateproof/sample
mkdir -p samples/TaskAppKMP/composeApp/src/commonMain/kotlin/io/stateproof/sample/ui
mkdir -p samples/TaskAppKMP/androidApp/src/main/{kotlin/io/stateproof/sample,res}
```

### Step 1.2: Copy Gradle wrapper
```bash
cp -r gradle samples/TaskAppKMP/ && cp gradlew gradlew.bat samples/TaskAppKMP/
```

### Step 1.3: `settings.gradle.kts`
```kotlin
rootProject.name = "stateproof-task-app-kmp-sample"
pluginManagement { repositories { mavenLocal(); gradlePluginPortal(); mavenCentral(); google() } }
dependencyResolutionManagement { repositories { mavenLocal(); mavenCentral(); google() } }
include(":composeApp")
include(":androidApp")
```

### Step 1.4: Root `build.gradle.kts`
```kotlin
plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("io.github.fshamim.stateproof") version "0.8.0-alpha01" apply false
}
```

### Step 1.5: `gradle.properties`
```properties
kotlin.code.style=official
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m
kotlin.mpp.androidSourceSetLayoutVersion=2
```

### Step 1.6: `composeApp/build.gradle.kts`
```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    id("io.github.fshamim.stateproof")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    jvm("desktop") { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    iosX64(); iosArm64(); iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.fshamim:stateproof-core:0.8.0-alpha01")
            implementation("io.github.fshamim:stateproof-annotations:0.8.0-alpha01")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        androidMain.dependencies {
            implementation("io.github.fshamim:stateproof-navigation:0.8.0-alpha01")  // drop if unavailable
            implementation("androidx.navigation:navigation-compose:2.7.7")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
        }
        val desktopMain by getting {
            dependencies {
                implementation("io.github.fshamim:stateproof-compose:0.8.0-alpha01")
                implementation(compose.desktop.currentOs)
            }
        }
        iosMain.dependencies {
            implementation("io.github.fshamim:stateproof-compose:0.8.0-alpha01")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
        val desktopTest by getting {
            dependencies {
                implementation("io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha01")
            }
        }
    }
}

android {
    namespace = "io.stateproof.sample"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "io.stateproof.sample.MainKt"
    }
}

stateproof {
    autoDiscovery.set(false)
    stateMachineFactoryFqn.set("io.stateproof.sample.TaskProofIntrospectionKt#createTaskProofStateMachineForIntrospection")
    initialState.set("Splash")
    testDir.set(layout.projectDirectory.dir("src/desktopTest/kotlin/generated/stateproof"))
    testPackage.set("io.stateproof.sample.generated")
    testClassName.set("GeneratedTaskProofStateMachineTest")
    stateMachineFactory.set("io.stateproof.sample.createTaskProofStateMachineForIntrospection()")
    eventClassPrefix.set("AppEvent")
    additionalImports.set(listOf("io.stateproof.sample.*"))
    diagramOutputDir.set(layout.buildDirectory.dir("stateproof/diagrams"))
    viewerOutputDir.set(layout.buildDirectory.dir("stateproof/viewer"))
}
```

### Step 1.7: `androidApp/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "io.stateproof.sample.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "io.stateproof.sample"
        minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "0.8.0-alpha01"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":composeApp"))
    // No Compose BOM — rely on transitive deps from composeApp (JetBrains Compose)
    implementation("androidx.activity:activity-compose:1.8.2")
}
```

### Step 1.8-1.9: Android manifests
- `androidApp/src/main/AndroidManifest.xml` — application with MainActivity launcher
- `composeApp/src/androidMain/AndroidManifest.xml` — empty `<manifest />`

### Step 1.10: Verify
```bash
cd samples/TaskAppKMP && ./gradlew :composeApp:tasks --all
```

---

## Phase 2: State Machine Modeling

### Step 2.1: `AppState.kt` — 10 states (sealed class)
States: Splash, Login, Authenticating, AuthError(reason), LoadingTasks, TaskList(tasks), TaskDetail(task), CreateTask, SavingTask, Settings.

### Step 2.2: `AppEvent.kt` — 19 events (sealed class)
User events: OnAppStart, OnLoginSubmit(u,p), OnSelectTask(id), OnCreateTaskTap, OnSaveTask(t,d), OnToggleTask(id), OnDeleteTask, OnSettingsTap, OnLogout, OnBack, OnRetry.
Side-effect-emitted: OnAuthSuccess(token), OnAuthFailed(reason), OnTasksLoaded(tasks), OnTasksLoadFailed(reason), OnTaskSaved(task), OnTaskSaveFailed(reason), OnTaskDeleted(id), OnTaskToggled(id, completed).

### Step 2.3: `Repositories.kt` — interfaces + result types

### Step 2.4: `TaskProofStateMachine.kt` — runtime machine with real side effects
Features demonstrated:
- **Guards**: `condition("credentials non-empty") { ... } then { ... }` on Login, `condition("title non-empty")` on CreateTask
- **Side effects with emits**: Auth calls, task CRUD, all with `sideEffect { } emits (...)`
- **Back handling**: Every state has explicit `on<AppEvent.OnBack>` transition
- **Data-carrying states**: TaskList(tasks), TaskDetail(task), AuthError(reason)
- **Chained side effects**: Auth success -> load tasks

### Step 2.5: `TaskProofIntrospection.kt` — deterministic introspection factory
- `@StateProofStateMachine` annotation (stateproof-annotations)
- `Dispatchers.Unconfined` for deterministic execution
- Guards return true, side effects return null
- Graph metadata captured in `emits()` declarations

### Step 2.6: Verify
```bash
./gradlew :composeApp:compileKotlinDesktop
```

---

## Phase 3: Compose UI

### Step 3.1: expect/actual `collectAsComposeState()`
- commonMain: `expect fun <S, E> StateMachine<S, E>.collectAsComposeState(): State<S>`
- desktopMain + iosMain: `actual` uses `stateproof-compose`'s `collectAsState()`
- androidMain: `actual` uses `stateproof-navigation`'s lifecycle-aware `collectAsState()`
- Fallback: `stateMachine.state.collectAsState()` (verified: `StateFlow<STATE>`)

### Step 3.2: `App.kt` — screens-as-states with `when(state)`
### Step 3.3: Screen composables (8 screens, pure callbacks)
### Step 3.4: `FakeRepositories.kt` — demo data
### Step 3.5: Verify compilation

---

## Phase 4: Platform Entry Points

### Step 4.1: Desktop `Main.kt` — `application { Window { App(sm) } }`
### Step 4.2: Android `MainActivity.kt` (in `androidApp`) — `setContent { App(sm) }`
### Step 4.3: iOS `MainViewController.kt` — `ComposeUIViewController { App(sm) }`
### Step 4.4: Verify
```bash
./gradlew :composeApp:compileKotlinDesktop && ./gradlew :androidApp:compileDebugKotlin
```

---

## Phase 5: Android StateProofNavHost (conditional on NAVIGATION_AVAILABLE)

### Step 5.1: `AndroidNavApp.kt` in `composeApp/src/androidMain/`
Demonstrates `StateProofNavHost` with:
- `splashScreen<Splash>`, `homeScreen<Login>`, `mainScreen<TaskList>`, `detailScreen<TaskDetail>`
- `onBack { AppEvent.OnBack }` — automatic back handling
- `StateProofAnimations` presets

Fallback: If unavailable, skip entirely. `App.kt` works on Android too.

---

## Phase 6: Test Generation

### Step 6.1: `./gradlew :composeApp:stateproofScan`
### Step 6.2: `./gradlew :composeApp:stateproofSyncAll` — generates exhaustive DFS tests
### Step 6.3: Fill generated test stubs using Phase 0.7 verified API:
```kotlin
val sm = createTaskProofStateMachineForIntrospection()
sm.onEvent(/* events from path */)
sm.awaitIdle()
assertContentEquals(expectedTransitions, sm.getTransitionLog())
sm.close()
```
### Step 6.4: `./gradlew :composeApp:desktopTest`

---

## Phase 7: Diagrams & Viewer

### Step 7.1: `./gradlew :composeApp:stateproofDiagrams` -> PlantUML + Mermaid
### Step 7.2: `./gradlew :composeApp:stateproofViewer` -> Interactive HTML (Cytoscape.js)
### Step 7.3: `ViewerGenerationTest.kt` — programmatic `toStateGraph()` -> `renderDiagrams()` + `renderViewer()`

---

## Phase 8: Validation & README

### Step 8.1: Smoke tests (initial state, transitions)
### Step 8.2: Full verification
```bash
./gradlew :composeApp:desktopTest
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:stateproofScan
./gradlew :composeApp:stateproofSyncAll
./gradlew :composeApp:stateproofDiagrams
./gradlew :composeApp:stateproofViewer
./gradlew build
```
### Step 8.3: README.md with Module Coverage Map + KSP transparency note

---

## Module Coverage Map

| Module | Where | How |
|--------|-------|-----|
| stateproof-core | commonMain | StateMachine DSL, all transitions |
| stateproof-compose | desktopMain, iosMain | `collectAsState()` extension |
| stateproof-navigation | androidMain | `StateProofNavHost` with screen mapping |
| stateproof-annotations | commonMain | `@StateProofStateMachine` on introspection factory |
| stateproof-ksp | README documented | Annotation format shown; autoDiscovery=false for KMP compat |
| stateproof-viewer | desktopTest | `toStateGraph()` -> `renderViewer()`/`renderDiagrams()` |
| stateproof-gradle-plugin | build.gradle.kts | `stateproof { }` config + all Gradle tasks |

## Debate History

| Round | Severity | Key Changes |
|-------|----------|-------------|
| v1 -> v2 | 7 -> 4 | Added Phase 0 discovery, fallbacks, source citations, error recovery, expect/actual pattern |
| v2 -> v3 | 4 -> 2 | Added API verification (Step 0.7), substitution variables (Step 0.8), androidApp module, Gradle wrapper, KSP transparency |
| v3 final | 2 | Compose BOM fix, desktop run task. Converged. |
