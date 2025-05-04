import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    base
    alias(libs.plugins.dependencies)
}

tasks.withType<Wrapper> {
    gradleVersion = libs.versions.gradle.get()
    distributionType = Wrapper.DistributionType.ALL
}

tasks.withType<DependencyUpdatesTask> {
    gradleReleaseChannel = "current"
    rejectVersionIf {
        listOf("alpha", "beta", "rc", "cr", "m", "eap", "pr", "dev").any {
            candidate.version.contains(it, ignoreCase = true)
        }
    }
}
