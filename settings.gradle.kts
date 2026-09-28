pluginManagement {
    repositories {
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
    repositories {
        google()
        mavenCentral()
        // JitPack — required by base-application AAR transitive deps (e.g. com.github.ybq:Android-SpinKit).
        maven { url = uri("https://jitpack.io") }
        // The base-application AAR is a local file with no POM. It is served through flatDir rather
        // than `files(...)` because AGP rejects direct local .aar file dependencies inside an
        // android-library module, which is what base-application-wrapper is.
        flatDir { dirs(rootDir.resolve("base-application-wrapper/libs")) }
    }
}

rootProject.name = "QRScanner"
include(":app")
include(":base-application-wrapper")
