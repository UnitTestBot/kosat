plugins {
    kotlin("multiplatform")
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
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
