package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.RefactorSelectionGroup
import com.internal.refactorassistant.model.SafetyLevel
import com.internal.refactorassistant.selection.TypeGrouping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeGroupingTest {
    @Test
    fun `activity maps to activity group`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
        )

        assertEquals(RefactorSelectionGroup.ACTIVITY, TypeGrouping.primaryGroup(item))
    }

    @Test
    fun `review required item also matches review required group`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.LAYOUT,
            oldName = "dialog_main",
            safetyLevel = SafetyLevel.REVIEW_REQUIRED,
        )

        assertTrue(RefactorSelectionGroup.REVIEW_REQUIRED in TypeGrouping.matchingGroups(item))
    }
}
