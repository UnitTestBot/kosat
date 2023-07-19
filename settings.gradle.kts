rootProject.name = "kosat"

plugins {
    `gradle-enterprise`
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

fun myInclude(name: String) {
    include(name)
    project(":$name").projectDir = file("${rootProject.name}-$name")
}

myInclude("core")
myInclude("cli")
myInclude("web")
