plugins {
    id("application-conventions")
}

dependencies {
    implementation(libs.clikt)
    implementation(libs.kotlin.retry)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.openrs2.buffer)
    implementation(libs.openrs2.cache)
    implementation(libs.openrs2.util)
    implementation(projects.js5)
}

application {
    mainClass.set("me.kfricilone.kached.Js5DownloaderCommandKt")
}

kotlinPublications {
    publication {
        publicationName.set("downloader")
        description.set("Tool to download osrs cache")
    }
}
