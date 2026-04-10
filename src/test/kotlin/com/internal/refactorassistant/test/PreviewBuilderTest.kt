package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.preview.PreviewBuilder
import com.internal.refactorassistant.preview.ReviewValidationService
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewBuilderTest {
    @Test
    fun `preview summary counts selected skipped and blocked`() {
        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val itemA = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "a",
                type = RefactorItemType.ACTIVITY,
                oldName = "MainActivity",
            ),
            selectedNewName = "HomeActivity",
            applySelected = true,
        )
        val itemB = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "b",
                type = RefactorItemType.ACTIVITY,
                oldName = "MainSettingsActivity",
            ),
            selectedNewName = "HomeActivity",
            applySelected = true,
        )
        val itemC = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "c",
                type = RefactorItemType.LAYOUT,
                oldName = "activity_main",
            ),
            selectedNewName = "activity_home",
            applySelected = false,
        )

        val preview = PreviewBuilder(validator).build(listOf(itemA, itemB, itemC))

        assertEquals(1, preview.summary.selectedCount)
        assertEquals(1, preview.summary.skippedCount)
        assertEquals(1, preview.summary.blockedCount)
    }
}
