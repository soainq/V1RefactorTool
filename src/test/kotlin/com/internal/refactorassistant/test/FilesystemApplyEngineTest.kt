package com.internal.refactorassistant.test

import com.internal.refactorassistant.executor.FilesystemApplyEngine
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.SessionItemResult
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.preview.PreviewBuilder
import com.internal.refactorassistant.preview.ReviewValidationService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `rename png file without reading content`() {
        val root = Files.createTempDirectory("apply-engine-png")
        val drawableDir = Files.createDirectories(root.resolve("res/drawable"))
        val pngFile = drawableDir.resolve("ic_setting.png")
        Files.write(pngFile, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val drawableItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "png",
                type = RefactorItemType.DRAWABLE,
                oldName = "ic_setting",
                absolutePath = pngFile.toString(),
            ),
            selectedNewName = "ic_preference",
            applySelected = true,
        )

        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val preview = PreviewBuilder(validator).build(listOf(drawableItem))
        val outcome = FilesystemApplyEngine(validator).apply(
            reviewItems = listOf(drawableItem),
            previewPlan = preview,
            referenceFiles = listOf(pngFile.toString()),
            settings = TestFixtures.settings(),
        )

        assertTrue(Files.exists(drawableDir.resolve("ic_preference.png")))
        assertEquals(SessionItemResult.APPLIED, outcome.itemResults["png"])
    }

    @Test
    fun `rename webp file without reading content`() {
        val root = Files.createTempDirectory("apply-engine-webp")
        val drawableDir = Files.createDirectories(root.resolve("res/drawable"))
        val webpFile = drawableDir.resolve("ic_done.webp")
        Files.write(webpFile, byteArrayOf(0x52, 0x49, 0x46, 0x46))

        val drawableItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "webp",
                type = RefactorItemType.DRAWABLE,
                oldName = "ic_done",
                absolutePath = webpFile.toString(),
            ),
            selectedNewName = "ic_complete",
            applySelected = true,
        )

        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val preview = PreviewBuilder(validator).build(listOf(drawableItem))
        val outcome = FilesystemApplyEngine(validator).apply(
            reviewItems = listOf(drawableItem),
            previewPlan = preview,
            referenceFiles = listOf(webpFile.toString()),
            settings = TestFixtures.settings(),
        )

        assertTrue(Files.exists(drawableDir.resolve("ic_complete.webp")))
        assertEquals(SessionItemResult.APPLIED, outcome.itemResults["webp"])
    }

    @Test
    fun `rename xml file with text processing`() {
        val root = Files.createTempDirectory("apply-engine-xml")
        val layoutDir = Files.createDirectories(root.resolve("res/layout"))
        val layoutFile = layoutDir.resolve("activity_main.xml")
        Files.writeString(
            layoutFile,
            """
            <LinearLayout>
                <include layout="@layout/activity_main" />
            </LinearLayout>
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val layoutItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "xml",
                type = RefactorItemType.LAYOUT,
                oldName = "activity_main",
                absolutePath = layoutFile.toString(),
            ),
            selectedNewName = "activity_home",
            applySelected = true,
        )

        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val preview = PreviewBuilder(validator).build(listOf(layoutItem))
        val outcome = FilesystemApplyEngine(validator).apply(
            reviewItems = listOf(layoutItem),
            previewPlan = preview,
            referenceFiles = listOf(layoutFile.toString()),
            settings = TestFixtures.settings(),
        )

        val renamedFile = layoutDir.resolve("activity_home.xml")
        assertTrue(Files.exists(renamedFile))
        assertTrue(Files.readString(renamedFile, StandardCharsets.UTF_8).contains("@layout/activity_home"))
        assertEquals(SessionItemResult.APPLIED, outcome.itemResults["xml"])
    }

    @Test
    fun `apply does not crash when a binary file is present`() {
        val root = Files.createTempDirectory("apply-engine-mixed")
        val srcDir = Files.createDirectories(root.resolve("src"))
        val drawableDir = Files.createDirectories(root.resolve("res/drawable"))
        val kotlinFile = srcDir.resolve("MainActivity.kt")
        val pngFile = drawableDir.resolve("ic_setting.png")

        Files.writeString(kotlinFile, "class MainActivity\n", StandardCharsets.UTF_8)
        Files.write(pngFile, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val kotlinItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "kotlin",
                type = RefactorItemType.KOTLIN_FILE,
                oldName = "MainActivity",
                absolutePath = kotlinFile.toString(),
            ),
            selectedNewName = "HomeActivity",
            applySelected = true,
        )
        val drawableItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "binary",
                type = RefactorItemType.DRAWABLE,
                oldName = "ic_setting",
                absolutePath = pngFile.toString(),
            ),
            selectedNewName = "ic_preference",
            applySelected = true,
        )

        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val preview = PreviewBuilder(validator).build(listOf(kotlinItem, drawableItem))
        val outcome = FilesystemApplyEngine(validator).apply(
            reviewItems = listOf(kotlinItem, drawableItem),
            previewPlan = preview,
            referenceFiles = listOf(kotlinFile.toString(), pngFile.toString()),
            settings = TestFixtures.settings(),
        )

        assertEquals(SessionItemResult.APPLIED, outcome.itemResults["kotlin"])
        assertEquals(SessionItemResult.APPLIED, outcome.itemResults["binary"])
        assertTrue(outcome.errors.isEmpty())
    }

    @Test
    fun `malformed input in one file does not crash the whole session`() {
        val root = Files.createTempDirectory("apply-engine-malformed")
        val srcDir = Files.createDirectories(root.resolve("src"))
        val valuesDir = Files.createDirectories(root.resolve("res/values"))
        val kotlinFile = srcDir.resolve("MainActivity.kt")
        val malformedXml = valuesDir.resolve("strings.xml")

        Files.writeString(kotlinFile, "class MainActivity\n", StandardCharsets.UTF_8)
        Files.write(malformedXml, byteArrayOf(0xC3.toByte(), 0x28))

        val kotlinItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "good",
                type = RefactorItemType.KOTLIN_FILE,
                oldName = "MainActivity",
                absolutePath = kotlinFile.toString(),
            ),
            selectedNewName = "HomeActivity",
            applySelected = true,
        )
        val stringsItem = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "bad",
                type = RefactorItemType.STRING,
                oldName = "main_title",
                absolutePath = malformedXml.toString(),
            ),
            selectedNewName = "home_title",
            applySelected = true,
        )

        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val preview = PreviewBuilder(validator).build(listOf(kotlinItem, stringsItem))
        val outcome = FilesystemApplyEngine(validator).apply(
            reviewItems = listOf(kotlinItem, stringsItem),
            previewPlan = preview,
            referenceFiles = listOf(kotlinFile.toString(), malformedXml.toString()),
            settings = TestFixtures.settings(),
        )

        assertEquals(SessionItemResult.APPLIED, outcome.itemResults["good"])
        assertEquals(SessionItemResult.FAILED, outcome.itemResults["bad"])
        assertTrue(outcome.errors.any { it.contains("strings.xml") })
        assertTrue(outcome.warningsByItemId["bad"]?.contains("not valid UTF-8", ignoreCase = true) == true)
    }

    @Test
    fun `apply progress is reported with counters`() {
        val root = Files.createTempDirectory("apply-engine-progress")
        val srcDir = Files.createDirectories(root.resolve("src"))
        val kotlinFile = srcDir.resolve("MainActivity.kt")
        Files.writeString(kotlinFile, "class MainActivity\n", StandardCharsets.UTF_8)

        val item = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "progress",
                type = RefactorItemType.KOTLIN_FILE,
                oldName = "MainActivity",
                absolutePath = kotlinFile.toString(),
            ),
            selectedNewName = "HomeActivity",
            applySelected = true,
        )

        val updates = mutableListOf<com.internal.refactorassistant.model.ApplyProgressUpdate>()
        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val preview = PreviewBuilder(validator).build(listOf(item))
        FilesystemApplyEngine(validator, progressReporter = { updates += it }).apply(
            reviewItems = listOf(item),
            previewPlan = preview,
            referenceFiles = listOf(kotlinFile.toString()),
            settings = TestFixtures.settings(),
        )

        assertTrue(updates.isNotEmpty())
        assertEquals(1, updates.last().processedItems)
        assertEquals(1, updates.last().successCount)
        assertTrue(updates.last().currentItemLabel.contains("MainActivity"))
    }
}
