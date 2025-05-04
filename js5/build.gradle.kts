plugins {
    id("library-conventions")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.network)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.openrs2.buffer)
                implementation(libs.openrs2.util)
            }
        }
    }
}

kotlinPublications {
    publication {
        publicationName.set("js5")
        description.set("Osrs Js5 protocol workers")
    }
}
