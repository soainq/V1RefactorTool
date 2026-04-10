package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.UsedNameEntry
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.suggest.SuggestionService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionServiceTest {
    private val service = SuggestionService()

    @Test
    fun `used names are filtered from suggestions by default`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
        )
        val registry = UsedNamesRegistry(
            namesByType = mapOf(
                RefactorItemType.ACTIVITY to listOf(
                    UsedNameEntry(
                        name = "HomeActivity",
                        lastUsedVersion = "reskin_v1",
                        lastUsedTimestamp = "2026-04-10T10:30:00+07:00",
                    )
                )
            )
        )

        val result = service.buildReviewItems(listOf(item), registry, showPreviouslyUsedNames = false).first()

        assertFalse(result.suggestions.any { it.value == "HomeActivity" })
        assertEquals("DashboardActivity", result.selectedNewName)
    }

    @Test
    fun `used names are shown when toggle is enabled`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.LAYOUT,
            oldName = "activity_main",
        )
        val registry = UsedNamesRegistry(
            namesByType = mapOf(
                RefactorItemType.LAYOUT to listOf(
                    UsedNameEntry(
                        name = "activity_home",
                        lastUsedVersion = "reskin_v1",
                        lastUsedTimestamp = "2026-04-10T10:30:00+07:00",
                    )
                )
            )
        )

        val result = service.buildReviewItems(listOf(item), registry, showPreviouslyUsedNames = true).first()

        assertTrue(result.suggestions.any { it.value == "activity_home" && it.usedMetadata.usedBefore })
    }
}
