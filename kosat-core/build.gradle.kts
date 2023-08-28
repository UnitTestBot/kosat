import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("multiplatform")
    id(Plugins.Dokka.id)
}

repositories {
    // JitPack repo for `kotlin-satlib`:
    maven(url = "https://jitpack.io")
}

kotlin {
    jvm()
    js {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(Libs.KotlinxCollectionsImmutable.kotlinx_collections_immutable)
                implementation(Libs.Okio.okio)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                // JUnit Testing
                implementation(Libs.JUnit.jupiter_api)
                runtimeOnly(Libs.JUnit.jupiter_engine)
                implementation(Libs.JUnit.jupiter_params)

                // Log4j Logging
                runtimeOnly(Libs.Log4j.log4j_slf4j_impl)

                // Dependencies
                implementation(Libs.KotlinSatlib.kotlin_satlib_core)
                implementation(Libs.Klock.klock_jvm)
            }

            tasks.withType<Test> {
                useJUnitPlatform()
                testLogging {
                    showExceptions = true
                    // showStandardStreams = true
                    events(
                        // TestLogEvent.PASSED,
                        TestLogEvent.FAILED,
                        TestLogEvent.SKIPPED,
                        TestLogEvent.STANDARD_ERROR
                    )
                    exceptionFormat = TestExceptionFormat.FULL
                }
            }
        }
    }
}
