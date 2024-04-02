plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    application
    `maven-publish`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
    implementation(libs.kotlin.retry)
    implementation(libs.openrs2.buffer)
    implementation(libs.openrs2.cache)
    implementation(libs.openrs2.util)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlin.inline.logger)

    implementation(projects.js5)
}

application {
    mainClass.set("me.kfricilone.kached.Js5DownloaderCommandKt")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
