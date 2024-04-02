import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import java.nio.charset.StandardCharsets
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    alias(libs.plugins.dependencies)

    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
}

val jvmVersion = JavaVersion.VERSION_21

description = "Osrs Cache Downloader in Kotlin"

tasks.withType<Wrapper> {
    gradleVersion = libs.versions.gradle.get()
}

val rejectVersionRegex = Regex("(?i)[._-](?:alpha|beta|rc|cr|m|dev)")
tasks.withType<DependencyUpdatesTask> {
    gradleReleaseChannel = "current"
    revision = "release"

    rejectVersionIf {
        candidate.version.contains(rejectVersionRegex)
    }
}

allprojects {
    group = "me.kfricilone.kached"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://repo.openrs2.org/repository/openrs2-snapshots") }
    }

    plugins.withType<BasePlugin> {
        configure<BasePluginExtension> {
            archivesName.set("${rootProject.name}-$name")
        }
    }

    plugins.withType<ApplicationPlugin> {
        tasks.named<JavaExec>("run") {
            standardInput = System.`in`
            workingDir = rootDir
        }

        dependencies {
            val runtimeOnly by configurations
            runtimeOnly(libs.logback.classic)
        }
    }

    plugins.withType<KotlinPluginWrapper> {
        configure<KotlinJvmProjectExtension> {
            explicitApi()

            jvmToolchain(jvmVersion.majorVersion.toInt())

            compilerOptions {
                optIn.add("kotlin.contracts.ExperimentalContracts")
                freeCompilerArgs.addAll(
                    "-Xinline-classes",
                    "-Xjsr305=strict"
                )
            }
        }
    }

    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(jvmVersion.majorVersion))
            }
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = StandardCharsets.UTF_8.name()
        options.release.set(jvmVersion.toString().toInt())
    }

    tasks.withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs = listOf("-ea")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = jvmVersion.toString()
    }

    tasks.withType<DetektCreateBaselineTask>().configureEach {
        jvmTarget = jvmVersion.toString()
    }

    plugins.withType<MavenPublishPlugin> {
        apply(plugin = "org.gradle.signing")

        configure<PublishingExtension> {
            publications.withType<MavenPublication> {
                pom {
                    url.set("https://github.com/kfricilone/${rootProject.name}")
                    inceptionYear.set("2022")

                    licenses {
                        license {
                            name.set("ISC License")
                            url.set("https://opensource.org/licenses/isc-license.txt")
                        }
                    }

                    developers {
                        developer {
                            name.set("Kyle Fricilone")
                            url.set("https://kfricilone.me")
                        }
                    }

                    scm {
                        connection.set("scm:git:https://github.com/kfricilone/${rootProject.name}")
                        developerConnection.set("scm:git:git@github.com:kfricilone/${rootProject.name}.git")
                        url.set("https://github.com/kfricilone/${rootProject.name}")
                    }

                    issueManagement {
                        system.set("GitHub")
                        url.set("https://github.com/kfricilone/${rootProject.name}/issues")
                    }

                    ciManagement {
                        system.set("GitHub")
                        url.set("https://github.com/kfricilone/${rootProject.name}/actions?query=workflow%3Aci")
                    }
                }
            }

            configure<SigningExtension> {
                useGpgCmd()
                sign(publications)
            }
        }
    }
}
