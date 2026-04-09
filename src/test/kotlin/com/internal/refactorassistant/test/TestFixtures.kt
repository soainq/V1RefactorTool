package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorOptions
import com.internal.refactorassistant.model.RefactorRequest
import com.internal.refactorassistant.model.ReferenceFileSnapshot
import com.internal.refactorassistant.model.ResourceFileSnapshot
import com.internal.refactorassistant.model.ResourceRenameKind
import com.internal.refactorassistant.model.ScanResult
import com.internal.refactorassistant.model.SourceFileSnapshot
import com.internal.refactorassistant.model.SourceLanguage
import com.internal.refactorassistant.model.ValueResourceSnapshot
import java.nio.file.Files
import java.nio.file.Path

object TestFixtures {
    fun sampleRequest() = RefactorRequest(
        oldFeatureName = "main",
        newFeatureName = "home",
        oldDisplayName = "Main",
        newDisplayName = "Home",
        oldPackagePrefix = "com.abc.def.main",
        newPackagePrefix = "com.abc.def.home",
        selectedModules = listOf("app"),
        options = RefactorOptions(
            renamePackages = true,
            renameClassesAndFiles = true,
            renameLayouts = true,
            renameDrawables = true,
            renameStrings = true,
            renameDimens = true,
            reformatCode = true,
            optimizeImports = true,
            createGitCommitAfterRefactor = false,
            dryRunOnly = true,
        ),
    )

    fun sampleScanResult(root: Path): ScanResult {
        val resolvedRoot = if (Files.exists(root.resolve("sampleProject"))) root.resolve("sampleProject") else root
        val appRoot = resolvedRoot.resolve("app/src/main")
        val javaRoot = appRoot.resolve("java")
        return ScanResult(
            modules = emptyList(),
            sourceFiles = listOf(
                SourceFileSnapshot(
                    moduleName = "app",
                    path = javaRoot.resolve("com/abc/def/main/MainActivity.kt").toString(),
                    sourceRootPath = javaRoot.toString(),
                    packageName = "com.abc.def.main",
                    fileName = "MainActivity.kt",
                    classNames = listOf("MainActivity"),
                    language = SourceLanguage.KOTLIN,
                ),
                SourceFileSnapshot(
                    moduleName = "app",
                    path = javaRoot.resolve("com/abc/def/main/MainFragment.kt").toString(),
                    sourceRootPath = javaRoot.toString(),
                    packageName = "com.abc.def.main",
                    fileName = "MainFragment.kt",
                    classNames = listOf("MainFragment"),
                    language = SourceLanguage.KOTLIN,
                ),
            ),
            resourceFiles = listOf(
                ResourceFileSnapshot(
                    moduleName = "app",
                    path = appRoot.resolve("res/layout/activity_main.xml").toString(),
                    directoryName = "layout",
                    resourceName = "activity_main",
                    extension = "xml",
                    kind = ResourceRenameKind.LAYOUT_FILE,
                ),
                ResourceFileSnapshot(
                    moduleName = "app",
                    path = appRoot.resolve("res/drawable/ic_main_setting.xml").toString(),
                    directoryName = "drawable",
                    resourceName = "ic_main_setting",
                    extension = "xml",
                    kind = ResourceRenameKind.DRAWABLE_FILE,
                ),
            ),
            valueResources = listOf(
                ValueResourceSnapshot(
                    moduleName = "app",
                    xmlFilePath = appRoot.resolve("res/values/strings.xml").toString(),
                    tagName = "string",
                    resourceName = "main_screen_title",
                    kind = ResourceRenameKind.STRING_VALUE,
                ),
                ValueResourceSnapshot(
                    moduleName = "app",
                    xmlFilePath = appRoot.resolve("res/values/dimens.xml").toString(),
                    tagName = "dimen",
                    resourceName = "main_content_padding",
                    kind = ResourceRenameKind.DIMEN_VALUE,
                ),
            ),
            referenceFiles = listOf(
                ReferenceFileSnapshot("app", javaRoot.resolve("com/abc/def/main/MainActivity.kt").toString(), "kt"),
                ReferenceFileSnapshot("app", javaRoot.resolve("com/abc/def/main/MainFragment.kt").toString(), "kt"),
                ReferenceFileSnapshot("app", appRoot.resolve("AndroidManifest.xml").toString(), "xml"),
                ReferenceFileSnapshot("app", appRoot.resolve("res/layout/activity_main.xml").toString(), "xml"),
                ReferenceFileSnapshot("app", appRoot.resolve("res/values/strings.xml").toString(), "xml"),
                ReferenceFileSnapshot("app", appRoot.resolve("res/values/dimens.xml").toString(), "xml"),
            ),
        )
    }
}
