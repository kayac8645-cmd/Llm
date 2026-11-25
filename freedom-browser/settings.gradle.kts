// Optional: -PmavenCentralMirror=<url> (or env MAVEN_CENTRAL_MIRROR) routes Maven Central through a
// mirror, e.g. https://maven-central.storage-download.googleapis.com/maven2/ when repo.maven.apache.org
// rate-limits a CI/sandbox. Off by default.
pluginManagement {
    val mirror = providers.gradleProperty("mavenCentralMirror").orElse(providers.environmentVariable("MAVEN_CENTRAL_MIRROR")).orNull
    repositories {
        if (mirror != null) maven(mirror)
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val mirror = providers.gradleProperty("mavenCentralMirror").orElse(providers.environmentVariable("MAVEN_CENTRAL_MIRROR")).orNull
    repositories {
        if (mirror != null) maven(mirror)
        google()
        mavenCentral()
    }
}

rootProject.name = "LumoBrowser"

include(":shared")
include(":androidApp")
