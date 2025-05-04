plugins {
    application
    id("compatibility-conventions")
    id("detekt-conventions")
    id("kotlinter-conventions")
    id("jvm-conventions")
    id("publish-conventions")
}

dependencies {
    runtimeOnly(libs.logback.classic)
}
