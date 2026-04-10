package com.internal.refactorassistant.selection

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.RefactorSelectionGroup
import com.internal.refactorassistant.model.ScannedRefactorItem
import com.internal.refactorassistant.model.SafetyLevel

object TypeGrouping {
    val orderedGroups: List<RefactorSelectionGroup> = listOf(
        RefactorSelectionGroup.ACTIVITY,
        RefactorSelectionGroup.FRAGMENT,
        RefactorSelectionGroup.VIEWMODEL,
        RefactorSelectionGroup.ADAPTER,
        RefactorSelectionGroup.KOTLIN_CLASS_OR_FILE,
        RefactorSelectionGroup.FEATURE_PACKAGE,
        RefactorSelectionGroup.LAYOUT_FILE,
        RefactorSelectionGroup.DRAWABLE_FILE,
        RefactorSelectionGroup.STRING_KEY,
        RefactorSelectionGroup.DIMEN_KEY,
        RefactorSelectionGroup.OTHER_RESOURCE,
        RefactorSelectionGroup.REVIEW_REQUIRED,
        RefactorSelectionGroup.DO_NOT_TOUCH,
    )

    val refactorableGroups: List<RefactorSelectionGroup> = orderedGroups.filter { it != RefactorSelectionGroup.DO_NOT_TOUCH }

    fun primaryGroup(item: ScannedRefactorItem): RefactorSelectionGroup = when (item.type) {
        RefactorItemType.ACTIVITY -> RefactorSelectionGroup.ACTIVITY
        RefactorItemType.FRAGMENT -> RefactorSelectionGroup.FRAGMENT
        RefactorItemType.VIEWMODEL -> RefactorSelectionGroup.VIEWMODEL
        RefactorItemType.ADAPTER -> RefactorSelectionGroup.ADAPTER
        RefactorItemType.KOTLIN_FILE, RefactorItemType.KOTLIN_CLASS -> RefactorSelectionGroup.KOTLIN_CLASS_OR_FILE
        RefactorItemType.PACKAGE_CHILD -> RefactorSelectionGroup.FEATURE_PACKAGE
        RefactorItemType.LAYOUT -> RefactorSelectionGroup.LAYOUT_FILE
        RefactorItemType.DRAWABLE -> RefactorSelectionGroup.DRAWABLE_FILE
        RefactorItemType.STRING -> RefactorSelectionGroup.STRING_KEY
        RefactorItemType.DIMEN -> RefactorSelectionGroup.DIMEN_KEY
    }

    fun matchingGroups(item: ScannedRefactorItem): Set<RefactorSelectionGroup> {
        val groups = linkedSetOf(primaryGroup(item))
        if (item.safetyLevel == SafetyLevel.REVIEW_REQUIRED) {
            groups += RefactorSelectionGroup.REVIEW_REQUIRED
        }
        if (item.safetyLevel == SafetyLevel.DO_NOT_TOUCH) {
            groups += RefactorSelectionGroup.DO_NOT_TOUCH
        }
        return groups
    }

    fun isActive(item: ScannedRefactorItem, selectedGroups: Set<RefactorSelectionGroup>): Boolean =
        matchingGroups(item).any { it in selectedGroups }
}
