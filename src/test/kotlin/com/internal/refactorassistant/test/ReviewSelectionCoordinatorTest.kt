package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.RefactorSelectionGroup
import com.internal.refactorassistant.model.SafetyLevel
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.selection.ReviewSelectionCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewSelectionCoordinatorTest {
    @Test
    fun `select all types activates every refactorable group`() {
        val coordinator = coordinator()

        val state = coordinator.setAllTypesSelected(true)

        assertTrue(state.selectAllTypes)
        assertFalse(RefactorSelectionGroup.DO_NOT_TOUCH in state.selectedGroups)
        assertTrue(RefactorSelectionGroup.FEATURE_PACKAGE in state.selectedGroups)
    }

    @Test
    fun `select all items marks every active item selected`() {
        val coordinator = coordinator()

        val state = coordinator.setAllActiveItemsSelected(true)

        assertTrue(state.selectAllItems)
        assertTrue(state.reviewItems.all { it.applySelected })
    }

    @Test
    fun `type selection reload narrows active set`() {
        val coordinator = coordinator()

        val state = coordinator
            .setAllTypesSelected(false)
            .let { coordinator.setGroupSelected(RefactorSelectionGroup.ACTIVITY, true) }

        assertEquals(1, state.reviewItems.size)
        assertEquals(RefactorItemType.ACTIVITY, state.reviewItems.first().item.type)
    }

    @Test
    fun `manual edit is preserved after unrelated reload`() {
        val coordinator = coordinator()

        coordinator.setManualName("activity", "LandingActivity")
        val reloaded = coordinator.setGroupSelected(RefactorSelectionGroup.LAYOUT_FILE, true)

        assertEquals(
            "LandingActivity",
            reloaded.reviewItems.first { it.item.id == "activity" }.selectedNewName,
        )
    }

    @Test
    fun `group canonical edit propagates to all children`() {
        val coordinator = duplicateNameCoordinator()
        val initial = coordinator.state()
        val groupKey = initial.reviewItems.first().groupKey

        val updated = coordinator.setGroupCanonicalName(groupKey, "SharedHomeActivity")

        assertTrue(updated.reviewItems.all { it.selectedNewName == "SharedHomeActivity" })
        assertTrue(updated.reviewItems.all { !it.overrideApplied })
    }

    @Test
    fun `child override does not change group default`() {
        val coordinator = duplicateNameCoordinator()
        val initial = coordinator.state()
        val groupKey = initial.reviewItems.first().groupKey
        coordinator.setGroupCanonicalName(groupKey, "SharedHomeActivity")

        val updated = coordinator.setManualName("activity-b", "UniqueLandingActivity")

        assertEquals("SharedHomeActivity", updated.reviewItems.first { it.item.id == "activity-a" }.canonicalNewName)
        assertEquals("SharedHomeActivity", updated.reviewItems.first { it.item.id == "activity-a" }.selectedNewName)
        assertEquals("UniqueLandingActivity", updated.reviewItems.first { it.item.id == "activity-b" }.selectedNewName)
        assertTrue(updated.reviewItems.first { it.item.id == "activity-b" }.overrideApplied)
        assertFalse(updated.reviewItems.first { it.item.id == "activity-a" }.overrideApplied)
    }

    private fun coordinator(): ReviewSelectionCoordinator = ReviewSelectionCoordinator(
        allItems = listOf(
            TestFixtures.scannedItem(
                id = "activity",
                type = RefactorItemType.ACTIVITY,
                oldName = "MainActivity",
            ),
            TestFixtures.scannedItem(
                id = "layout",
                type = RefactorItemType.LAYOUT,
                oldName = "activity_main",
            ),
            TestFixtures.scannedItem(
                id = "package",
                type = RefactorItemType.PACKAGE_CHILD,
                oldName = "com.example.app.feature.main",
                safetyLevel = SafetyLevel.REVIEW_REQUIRED,
            ),
        ),
        registry = UsedNamesRegistry(),
    )

    private fun duplicateNameCoordinator(): ReviewSelectionCoordinator = ReviewSelectionCoordinator(
        allItems = listOf(
            TestFixtures.scannedItem(
                id = "activity-a",
                type = RefactorItemType.ACTIVITY,
                oldName = "MainActivity",
                absolutePath = "C:/tmp/featureA/MainActivity.kt",
            ),
            TestFixtures.scannedItem(
                id = "activity-b",
                type = RefactorItemType.ACTIVITY,
                oldName = "MainActivity",
                absolutePath = "C:/tmp/featureB/MainActivity.kt",
            ),
        ),
        registry = UsedNamesRegistry(),
    )
}
