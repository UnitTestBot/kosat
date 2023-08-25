plugins {
    kotlin("jvm")
    application
    id(Plugins.Shadow.id)
}

dependencies {
    implementation(project(":core"))
    implementation(Libs.Clikt.clikt)
    implementation(Libs.Okio.okio)
}

application {
    mainClass.set("org.kosat.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
}

tasks.startScripts {
    applicationName = rootProject.name
}

tasks.shadowJar {
    archiveBaseName.set(rootProject.name)
    archiveClassifier.set("")
    archiveVersion.set("")
}
