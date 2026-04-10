import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "com.internal"
version = "0.2.0"

val androidStudioVersion = providers.gradleProperty("androidStudioVersion").get()
val androidStudioLocalPath = providers.gradleProperty("androidStudioLocalPath").orNull?.takeIf { it.isNotBlank() }
val pluginSinceBuild = providers.gradleProperty("pluginSinceBuild").get()
val pluginUntilBuild = providers.gradleProperty("pluginUntilBuild").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (androidStudioLocalPath != null) {
            local(androidStudioLocalPath)
        } else {
            androidStudio(androidStudioVersion)
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set(pluginSinceBuild)
            untilBuild.set(pluginUntilBuild)
        }
        name.set("Internal Project Refactor Assistant")
        description.set(
            """
            Minimal Android Studio plugin that previews package, Kotlin, and layout rename candidates without changing files.
            """.trimIndent()
        )
        vendor {
            name.set("Internal Tooling")
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.0.0"
    }
}
