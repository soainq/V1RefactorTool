package com.internal.refactorassistant.test

import com.internal.refactorassistant.executor.FilesystemApplyEngine
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.preview.PreviewBuilder
import com.internal.refactorassistant.preview.ReviewValidationService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilesystemApplyEngineTest {
    @Test
    fun `apply changes selected items only`() {
        val root = Files.createTempDirectory("apply-engine")
        val srcDir = Files.createDirectories(root.resolve("src"))
        val layoutDir = Files.createDirectories(root.resolve("res/layout"))
        val kotlinFile = srcDir.resolve("MainActivity.kt")
        val layoutFile = layoutDir.resolve("activity_main.xml")

        Files.writeString(kotlinFile, "package com.example\n\nclass MainActivity\n")
        Files.writeString(layoutFile, "<LinearLayout />")

        val fileItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "file",
                type = RefactorItemType.KOTLIN_FILE,
                oldName = "MainActivity",
                absolutePath = kotlinFile.toString(),
            ),
            selectedNewName = "HomeActivity",
            applySelected = true,
        )
        val layoutItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "layout",
                type = RefactorItemType.LAYOUT,
                oldName = "activity_main",
                absolutePath = layoutFile.toString(),
            ),
            selectedNewName = "activity_home",
            applySelected = false,
        )

        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val preview = PreviewBuilder(validator).build(listOf(fileItem, layoutItem))
        val outcome = FilesystemApplyEngine(validator).apply(
            reviewItems = listOf(fileItem, layoutItem),
            previewPlan = preview,
            referenceFiles = listOf(kotlinFile.toString(), layoutFile.toString()),
            settings = TestFixtures.settings(),
        )

        assertTrue(Files.exists(srcDir.resolve("HomeActivity.kt")))
        assertFalse(Files.exists(srcDir.resolve("MainActivity.kt")))
        assertTrue(Files.exists(layoutFile))
        assertTrue(outcome.changedFiles.any { it.endsWith("HomeActivity.kt") })
    }
}
