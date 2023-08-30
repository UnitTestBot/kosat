@file:Suppress("PublicApiImplicitType", "MemberVisibilityCanBePrivate", "unused", "ConstPropertyName")

object Versions {
    const val clikt = "4.1.0"
    const val git_versioning = "6.4.2"
    const val gradle_download = "5.4.0"
    const val gradle_versions = "0.47.0"
    const val junit = "5.9.1"
    const val klock = "4.0.2"
    const val kotlin = "1.9.0"
    const val kotlin_logging = "3.0.5"
    const val kotlin_satlib = "0.24.2"
    const val kotlin_wrappers = "1.0.0-pre.619"
    const val kotlinx_collections = "0.3.5"
    const val kotlinx_coroutines = "1.7.2"
    const val log4j = "2.20.0"
    const val okio = "3.4.0"
    const val react_window = "1.8.9"
    const val shadow = "8.1.1"
}

object Libs {
    // https://github.com/ajalt/clikt
    object Clikt {
        const val version = Versions.clikt
        const val clikt = "com.github.ajalt.clikt:clikt:$version"
    }

    // https://github.com/junit-team/junit5
    object JUnit {
        const val version = Versions.junit
        const val jupiter_api = "org.junit.jupiter:junit-jupiter-api:$version"
        const val jupiter_engine = "org.junit.jupiter:junit-jupiter-engine:$version"
        const val jupiter_params = "org.junit.jupiter:junit-jupiter-params:$version"
    }

    // https://docs.korge.org/klock
    object Klock {
        const val version = Versions.klock
        const val klock_jvm = "com.soywiz.korlibs.klock:klock-jvm:$version"
    }

    // https://github.com/square/okio
    object Okio {
        const val version = Versions.okio
        const val okio = "com.squareup.okio:okio:$version"
    }

    // https://github.com/Lipen/kotlin-satlib
    object KotlinSatlib {
        const val version = Versions.kotlin_satlib
        const val kotlin_satlib_core = "com.github.Lipen.kotlin-satlib:core:$version"
    }

    // https://github.com/MicroUtils/kotlin-logging
    object KotlinLogging {
        const val version = Versions.kotlin_logging
        const val kotlin_logging = "io.github.microutils:kotlin-logging:$version"
    }

    // https://github.com/apache/logging-log4j2
    object Log4j {
        const val version = Versions.log4j
        const val log4j_slf4j_impl = "org.apache.logging.log4j:log4j-slf4j-impl:$version"
    }

    // https://github.com/Kotlin/kotlinx.coroutines
    object KotlinxCoroutines {
        const val version = Versions.kotlinx_coroutines
        const val kotlinx_coroutines_core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$version"
    }

    // https://github.com/Kotlin/kotlinx.collections.immutable
    object KotlinxCollectionsImmutable {
        const val version = Versions.kotlinx_collections
        const val kotlinx_collections_immutable = "org.jetbrains.kotlinx:kotlinx-collections-immutable:$version"
    }
}

object Plugins {
    // https://github.com/qoomon/gradle-git-versioning-plugin
    object GitVersioning {
        const val version = Versions.git_versioning
        const val id = "me.qoomon.git-versioning"
    }

    // https://github.com/michel-kraemer/gradle-download-task
    object GradleDownload {
        const val version = Versions.gradle_download
        const val id = "de.undercouch.download"
    }

    // https://github.com/ben-manes/gradle-versions-plugin
    object GradleVersions {
        const val version = Versions.gradle_versions
        const val id = "com.github.ben-manes.versions"
    }

    // https://github.com/johnrengelman/shadow
    object Shadow {
        const val version = Versions.shadow
        const val id = "com.github.johnrengelman.shadow"
    }
}
