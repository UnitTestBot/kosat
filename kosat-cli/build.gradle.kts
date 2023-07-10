import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

repositories {
    // JitPack repo for `kotlin-satlib`:
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(project(":core"))
    implementation(Libs.Klock.klock_jvm)

    testImplementation(kotlin("test"))
    testImplementation(Libs.JUnit.jupiter_api)
    testRuntimeOnly(Libs.JUnit.jupiter_engine)
    testImplementation(Libs.JUnit.jupiter_params)

    testImplementation(Libs.KotlinSatlib.kotlin_satlib_core)

    testRuntimeOnly(Libs.Log4j.log4j_slf4j_impl)
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
