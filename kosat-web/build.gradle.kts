plugins {
    kotlin("multiplatform")
}

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
                implementation(enforcedPlatform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:1.0.0-pre.612"))
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-mui")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-mui-icons")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion")
            }
        }
    }
}
