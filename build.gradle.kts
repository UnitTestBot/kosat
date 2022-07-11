import de.undercouch.gradle.tasks.download.DownloadAction


plugins {
    kotlin("multiplatform") version "1.6.21"
    id("de.undercouch.download") version "4.1.1"
    application
}

group = "org.kosat"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Lipen:kotlin-satlib:master-SNAPSHOT")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.2")
    implementation("org.apache.logging.log4j:log4j-core:2.17.2")
}

/*
tasks.withType<Test> {
    systemProperty("java.library.path", "/home/runner/work/kosat/kosat/src/jvmMain/resources/lib.linux64")
}*/

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
        val urlTemplate = "https://github.com/Lipen/kotlin-satlib/releases/download/0.24.2/%s"
        val libResDir = projectDir.resolve("src/main/resources/lib/$osArch")

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


kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(LEGACY) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
//    js(LEGACY) {
//        binaries.executable()
//        browser {
//            commonWebpackConfig {
//                cssSupport.enabled = true
//            }
//        }
//    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-server-netty:1.6.7")
                implementation("io.ktor:ktor-html-builder:1.6.7")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:17.0.2-pre.290-kotlin-1.6.10")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:17.0.2-pre.290-kotlin-1.6.10")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-css:17.0.2-pre.290-kotlin-1.6.10")
            }
        }
        val jsTest by getting
    }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().download = true
    // or true for default behavior
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().download = true
    // or true for default behavior
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().ignoreScripts = false
}


application {
    mainClass.set("org.kosat.ServerKt")
}

tasks.named<Copy>("jvmProcessResources") {
    val jsBrowserDistribution = tasks.named("jsBrowserDistribution")
    from(jsBrowserDistribution)
}

tasks.named<JavaExec>("run") {
    dependsOn(tasks.named<Jar>("jvmJar"))
    classpath(tasks.named<Jar>("jvmJar"))
}