import de.undercouch.gradle.tasks.download.DownloadAction

plugins {
    kotlin("multiplatform") version Versions.kotlin apply false
    id("de.undercouch.download") version "4.1.1"
    with(Plugins.GradleVersions) { id(id) version (version) }
    // with(Plugins.GitVersioning) { id(id) version (version) }
    `maven-publish`
    idea
}

allprojects {
    repositories {
        mavenCentral()
    }
}

group = "org.kosat"
version = "1.0-SNAPSHOT"

subprojects {
    group = "${rootProject.group}.${rootProject.name}"
    version = rootProject.version
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

fun Task.download(action: DownloadAction.() -> Unit) =
    download.configure(delegateClosureOf(action))

val osArch: String = run {
    val osName = System.getProperty("os.name")
    val os = when {
        osName.startsWith("Linux") -> "linux"
        osName.startsWith("Windows") -> "win"
        osName.startsWith("Mac OS X") || osName.startsWith("Darwin") -> "osx"
        else -> return@run "unknown"
    }
    val arch = when (System.getProperty("os.arch")) {
        "x86", "i386" -> "32"
        "x86_64", "amd64" -> "64"
        else -> return@run "unknown"
    }
    "$os$arch"
}

tasks.register("downloadLibs") {
    doLast {
        val urlTemplate = "https://github.com/Lipen/kotlin-satlib/releases/download/${Versions.kotlin_satlib}/%s"
        val libResDir = projectDir.resolve("kosat-core/src/jvmTest/resources/lib/$osArch")

        fun ensureDirExists(dir: File) {
            if (!dir.exists()) {
                check(dir.mkdirs()) { "Cannot create dirs for '$dir'" }
            }
            check(dir.exists()) { "'$dir' still does not exist" }
        }

        fun downloadLibs(names: List<String>, dest: File) {
            ensureDirExists(dest)
            download {
                src(names.map { urlTemplate.format(it) })
                dest(dest)
                tempAndMove(true)
            }
        }

        when (osArch) {
            "linux64" -> {
                val jLibs = listOf(
                    "libjminisat.so",
                    "libjglucose.so",
                    "libjcms.so",
                    "libjcadical.so"
                )
                downloadLibs(jLibs, libResDir)

                val solverLibs = listOf(
                    "libminisat.so",
                    "libglucose.so",
                    "libcryptominisat5.so",
                    "libcadical.so"
                )
                val solverLibDir = rootDir.resolve("libs")
                downloadLibs(solverLibs, solverLibDir)
            }

            "win64" -> {
                val jLibs = listOf(
                    "jminisat.dll",
                    "jglucose.dll",
                    "jcadical.dll",
                    "jcms.dll"
                )
                downloadLibs(jLibs, libResDir)

                val solverLibs = listOf(
                    "libminisat.dll",
                    "glucose.dll",
                    "cadical.dll",
                    "libcryptominisat5win.dll"
                )
                val solverLibDir = rootDir.resolve("libs")
                downloadLibs(solverLibs, solverLibDir)
            }

            else -> {
                error("$osArch is not supported, sorry")
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "8.2"
    distributionType = Wrapper.DistributionType.ALL
}

defaultTasks("clean", "build")
