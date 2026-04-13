package com.internal.refactorassistant.classify

import com.internal.refactorassistant.model.ProjectScanResult
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ScannedRefactorItem
import com.internal.refactorassistant.model.SafetyLevel
import com.internal.refactorassistant.rules.NamingRules

class ItemClassifier {
    fun classify(scanResult: ProjectScanResult): ProjectScanResult = scanResult.copy(
        items = scanResult.items.map(::classifyItem),
    )

    fun classifyItem(item: ScannedRefactorItem): ScannedRefactorItem = item.copy(
        safetyLevel = when (item.type) {
            RefactorItemType.ACTIVITY,
            RefactorItemType.FRAGMENT,
            RefactorItemType.VIEWMODEL,
            RefactorItemType.ADAPTER,
            -> SafetyLevel.SAFE_AUTO

            RefactorItemType.KOTLIN_FILE,
            RefactorItemType.KOTLIN_CLASS,
            -> if (needsReview(item.oldName)) SafetyLevel.REVIEW_REQUIRED else SafetyLevel.SAFE_AUTO

            RefactorItemType.LAYOUT,
            RefactorItemType.DRAWABLE,
            RefactorItemType.STRING,
            RefactorItemType.DIMEN,
            -> if (needsReview(item.oldName)) SafetyLevel.REVIEW_REQUIRED else SafetyLevel.SAFE_AUTO

            RefactorItemType.PACKAGE_CHILD,
            -> if (isProtectedPackage(item)) SafetyLevel.DO_NOT_TOUCH else SafetyLevel.REVIEW_REQUIRED
        },
    )

    private fun needsReview(name: String): Boolean =
        name.contains("dialog", ignoreCase = true) ||
            name.contains("widget", ignoreCase = true) ||
            name.contains("component", ignoreCase = true)

    private fun isProtectedPackage(item: ScannedRefactorItem): Boolean {
        val relative = item.details.packageRelativePath.orEmpty()
        val segments = relative.split('.')
        return segments.any { it in NamingRules.protectedPackageSegments }
    }
}
