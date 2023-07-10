plugins {
    kotlin("js")
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
}

dependencies {
    implementation(project(":core"))
    implementation(Libs.KotlinxCoroutines.kotlinx_coroutines_core)
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react:17.0.2-pre.290-kotlin-1.6.10")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:17.0.2-pre.290-kotlin-1.6.10")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-css:17.0.2-pre.290-kotlin-1.6.10")
}
