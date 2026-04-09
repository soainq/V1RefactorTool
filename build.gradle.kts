import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.internal"
version = "0.1.0"

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
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
    testImplementation(kotlin("test"))

    intellijPlatform {
        if (androidStudioLocalPath != null) {
            local(androidStudioLocalPath)
        } else {
            androidStudio(androidStudioVersion)
        }

        bundledPlugin("org.jetbrains.android")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
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
        vendor {
            name.set("Internal Tooling")
        }
        name.set("Internal Project Refactor Assistant")
        description.set(
            """
            Internal Android Studio plugin that duplicates template Android projects by generating a refactor plan,
            previewing changes, and applying package/source/resource renames with reporting.
            """.trimIndent()
        )
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set(pluginSinceBuild)
        untilBuild.set(pluginUntilBuild)
    }

    wrapper {
        gradleVersion = "8.10.2"
    }
}
