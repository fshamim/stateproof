plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}

allprojects {
    group = "io.stateproof"
    version = "0.1.0-SNAPSHOT"
}
