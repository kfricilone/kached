plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    `maven-publish`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.openrs2.buffer)
    implementation(libs.openrs2.util)
    implementation(libs.ktor.network)
    implementation(libs.kotlin.inline.logger)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
