plugins {
    kotlin("multiplatform")
}

fun kotlinw(target: String): String =
    "org.jetbrains.kotlin-wrappers:kotlin-$target"

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(Libs.KotlinxCoroutines.kotlinx_coroutines_core)
                implementation(enforcedPlatform(kotlinw("wrappers-bom:${Versions.kotlin_wrappers}")))
                implementation(kotlinw("react"))
                implementation(kotlinw("react-dom"))
                implementation(kotlinw("mui"))
                implementation(kotlinw("mui-icons"))
                implementation(kotlinw("emotion"))
                implementation(npm("react-window", Versions.react_window))
            }
        }
    }
}
