import org.jetbrains.kotlinx.publisher.githubRepo

plugins {
    alias(libs.plugins.kotlin.libs.publisher)
}

kotlinPublications {
    defaultGroup.set("me.kfricilone")
    defaultArtifactIdPrefix.set("${rootProject.name}-")
    fairDokkaJars.set(false)

    pom {
        inceptionYear.set("2021")

        licenses {
            license {
                name.set("ISC License")
                url.set("https://opensource.org/licenses/isc-license.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("kfricilone")
                name.set("Kyle Fricilone")
                url.set("https://github.com/kfricilone")
            }
        }

        githubRepo("kfricilone", rootProject.name)

        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/kfricilone/${rootProject.name}/issues")
        }

        ciManagement {
            system.set("GitHub Actions")
            url.set("https://github.com/kfricilone/${rootProject.name}/actions")
        }
    }

    localRepositories {
        defaultLocalMavenRepository()
    }
}

signing {
    useGpgCmd()
}
