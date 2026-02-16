import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}

val stateproofGroup = providers.gradleProperty("stateproofGroup").orNull ?: "io.github.fshamim"
val stateproofVersion = providers.gradleProperty("stateproofVersion").orNull ?: "0.8.0-alpha01"

allprojects {
    group = stateproofGroup
    version = stateproofVersion
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    val moduleName = project.name
                        .removePrefix("stateproof-")
                        .split("-")
                        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercaseChar() } }
                    name.set("StateProof $moduleName")
                    description.set("StateProof module: ${project.name}")
                    url.set("https://github.com/stateproof/stateproof")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("fshamim")
                            name.set("Farhan Shamim")
                        }
                    }
                    scm {
                        url.set("https://github.com/stateproof/stateproof")
                        connection.set("scm:git:https://github.com/stateproof/stateproof.git")
                        developerConnection.set("scm:git:ssh://git@github.com/stateproof/stateproof.git")
                    }
                }
            }

            repositories {
                maven {
                    name = "Central"
                    url = uri(
                        providers.gradleProperty("centralRepositoryUrl").orNull
                            ?: "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                    )
                    credentials {
                        username =
                            providers.gradleProperty("centralUsername").orNull
                                ?: System.getenv("CENTRAL_USERNAME")
                        password =
                            providers.gradleProperty("centralPassword").orNull
                                ?: System.getenv("CENTRAL_PASSWORD")
                    }
                }
            }
        }

        pluginManager.apply("signing")
        extensions.configure<SigningExtension> {
            val signingKey =
                providers.gradleProperty("signingInMemoryKey").orNull ?: System.getenv("SIGNING_KEY")
            val signingPassword =
                providers.gradleProperty("signingInMemoryKeyPassword").orNull
                    ?: System.getenv("SIGNING_PASSWORD")

            if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType(PublishingExtension::class.java).publications)
            }
        }
    }
}
