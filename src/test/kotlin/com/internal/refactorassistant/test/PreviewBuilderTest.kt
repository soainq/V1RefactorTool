package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.preview.PreviewBuilder
import com.internal.refactorassistant.preview.ReviewValidationService
import com.internal.refactorassistant.ui.PreviewPlanDialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewBuilderTest {
    @Test
    fun `preview summary counts ready skipped and blocked`() {
        val validator = ReviewValidationService(emptyMap(), UsedNamesRegistry())
        val itemA = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "a",
                type = RefactorItemType.ACTIVITY,
                oldName = "BaseActivity",
            ),
            selectedNewName = "CoreActivity",
            applySelected = true,
        ).copy(groupKey = "ACTIVITY:baseactivity", canonicalNewName = "CoreActivity", groupSize = 2)
        val itemB = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                id = "b",
                type = RefactorItemType.ACTIVITY,
                oldName = "BaseActivity",
            ),
            selectedNewName = "CoreActivity",
            applySelected = true,
        ).copy(groupKey = "ACTIVITY:baseactivity", canonicalNewName = "CoreActivity", groupSize = 2)
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

        assertEquals(2, preview.summary.selectedCount)
        assertEquals(1, preview.summary.skippedCount)
        assertEquals(0, preview.summary.blockedCount)
        assertTrue(preview.rows.count { it.status == "READY" } == 2)
    }

    @Test
    fun `preview default columns are simplified`() {
        assertEquals(
            listOf("Type", "Before", "After", "Status", "Reason", "Path"),
            PreviewPlanDialog.DEFAULT_COLUMNS,
        )
        assertEquals(
            listOf(
                "Group Key",
                "Canonical New Name",
                "Group Size",
                "Override Status",
                "Suggestion Source",
                "Candidate Rank",
                "Safety",
                "Module",
            ),
            PreviewPlanDialog.ADVANCED_DETAIL_FIELDS,
        )
    }
}
