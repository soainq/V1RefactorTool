import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "com.internal"

val pluginBaseVersion = providers.gradleProperty("pluginBaseVersion").orElse("0.3.0").get()
val versionSequenceFile = rootDir.resolve(".internal-refactor-assistant/build-sequence.properties")
val producesVersionedBuild = gradle.startParameter.taskNames.any { requestedTask ->
    val taskName = requestedTask.substringAfterLast(':')
    taskName in setOf("build", "buildPlugin", "assemble", "jar", "composedJar", "publishPlugin", "runIde")
}
val buildSequenceIncrementMarker = "internal.refactorassistant.buildSequenceIncremented"

fun nextBuildSequence(): Int {
    versionSequenceFile.parentFile.mkdirs()
    val properties = Properties()
    if (versionSequenceFile.exists()) {
        versionSequenceFile.inputStream().use(properties::load)
    }
    val current = properties.getProperty("buildSequence")?.toIntOrNull() ?: 0
    val shouldIncrement = producesVersionedBuild &&
        gradle.parent == null &&
        System.getProperty(buildSequenceIncrementMarker) != "true"
    val next = if (shouldIncrement) current + 1 else current
    if (shouldIncrement) {
        properties["buildSequence"] = next.toString()
        properties["lastUpdatedUtc"] = OffsetDateTime.now(ZoneOffset.UTC).toString()
        versionSequenceFile.outputStream().use { output ->
            properties.store(output, "Local incremental build sequence")
        }
        System.setProperty(buildSequenceIncrementMarker, "true")
    }
    return next
}

val buildSequence = nextBuildSequence()
val pluginBuildVersion = "$pluginBaseVersion-build.$buildSequence"

version = pluginBuildVersion

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
        version.set(pluginBuildVersion)
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
    register("printBuildVersion") {
        group = "versioning"
        description = "Prints the current plugin version and local build sequence."
        doLast {
            println("pluginVersion=$pluginBuildVersion")
            println("buildSequence=$buildSequence")
            println("sequenceFile=${versionSequenceFile.absolutePath}")
        }
    }

    wrapper {
        gradleVersion = "9.0.0"
    }
}
