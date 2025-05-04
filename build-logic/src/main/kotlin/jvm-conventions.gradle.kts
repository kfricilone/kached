plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    explicitApi()

    jvmToolchain(libs.versions.java.get().toInt())

    dependencies {
        implementation(libs.kotlin.logging)

        testImplementation(kotlin("test-junit"))
    }
}
