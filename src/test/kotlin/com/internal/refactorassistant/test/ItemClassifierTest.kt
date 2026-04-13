package com.internal.refactorassistant.test

import com.internal.refactorassistant.classify.ItemClassifier
import com.internal.refactorassistant.model.ItemDetails
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.SafetyLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemClassifierTest {
    private val classifier = ItemClassifier()

    @Test
    fun `activity is safe auto`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.ACTIVITY,
            oldName = "MainActivity",
            safetyLevel = SafetyLevel.REVIEW_REQUIRED,
        )

        val result = classifier.classifyItem(item)

        assertEquals(SafetyLevel.SAFE_AUTO, result.safetyLevel)
    }

    @Test
    fun `protected package is do not touch`() {
        val item = TestFixtures.scannedItem(
            type = RefactorItemType.PACKAGE_CHILD,
            oldName = "com.example.app.core.main",
            details = ItemDetails(
                packageName = "com.example.app.core.main",
                rootPackage = "com.example.app",
                packageRelativePath = "core.main",
            ),
            safetyLevel = SafetyLevel.REVIEW_REQUIRED,
        )

        val result = classifier.classifyItem(item)

        assertEquals(SafetyLevel.DO_NOT_TOUCH, result.safetyLevel)
    }
}
