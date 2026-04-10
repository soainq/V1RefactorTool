import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "com.internal"
version = "0.3.0"

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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    testImplementation(kotlin("test"))

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

tasks.test {
    useJUnitPlatform()
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
            Android Studio plugin for repeated reskin/refactor workflows with scan, suggestion review, preview, apply, and local JSON history.
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
