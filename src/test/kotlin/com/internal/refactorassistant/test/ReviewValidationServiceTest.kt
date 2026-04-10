package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.UsedNameEntry
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.preview.ReviewValidationService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewValidationServiceTest {
    @Test
    fun `manual reuse emits warning without blocking`() {
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
        val validator = ReviewValidationService(emptyMap(), registry)
        val item = TestFixtures.reviewState(
            item = TestFixtures.scannedItem(
                type = RefactorItemType.ACTIVITY,
                oldName = "MainActivity",
            ),
            selectedNewName = "HomeActivity",
        )

        val validation = validator.validate(item, listOf(item))

        assertFalse(validation.blocked)
        assertTrue(validation.warnings.any { it.contains("used in a previous refactor version") })
    }
}
