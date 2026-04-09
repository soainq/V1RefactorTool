import org.jetbrains.intellij.platform.gradle.TestFrameworkType
//plugins {
//    id("org.jetbrains.intellij.platform.settings") version "2.5.0"
//}

rootProject.name = "internal-project-refactor-assistant"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
