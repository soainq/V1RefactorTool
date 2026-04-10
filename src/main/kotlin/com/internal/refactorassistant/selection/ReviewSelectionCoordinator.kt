package com.internal.refactorassistant.selection

import com.internal.refactorassistant.model.GroupSelectionInfo
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.RefactorSelectionGroup
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ReviewScreenState
import com.internal.refactorassistant.model.ScannedRefactorItem
import com.internal.refactorassistant.model.SafetyLevel
import com.internal.refactorassistant.model.SuggestionSource
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.suggest.SuggestionService

class ReviewSelectionCoordinator(
    private val allItems: List<ScannedRefactorItem>,
    private val registry: UsedNamesRegistry,
    private val existingNamesByType: Map<RefactorItemType, Set<String>> = emptyMap(),
    private val suggestionService: SuggestionService = SuggestionService(),
) {
    private val selectedGroups = defaultSelectedGroups(allItems).toMutableSet()
    private val itemSelectionOverrides = mutableMapOf<String, Boolean>()
    private val groupNameOverrides = mutableMapOf<String, String>()
    private val itemNameOverrides = mutableMapOf<String, String>()
    private var showPreviouslyUsedNames: Boolean = false

    init {
        suggestionService.buildReviewItems(
            items = currentActiveItems(),
            registry = registry,
            existingNamesByType = existingNamesByType,
            showPreviouslyUsedNames = showPreviouslyUsedNames,
        ).forEach { reviewItem ->
            itemSelectionOverrides[reviewItem.item.id] = reviewItem.applySelected
        }
    }

    fun state(): ReviewScreenState = rebuild()

    fun setGroupSelected(group: RefactorSelectionGroup, selected: Boolean): ReviewScreenState {
        if (selected) {
            selectedGroups += group
            allItems.filter { group in TypeGrouping.matchingGroups(it) }
                .forEach { item ->
                    if (item.safetyLevel != SafetyLevel.DO_NOT_TOUCH) {
                        itemSelectionOverrides[item.id] = true
                    }
                }
        } else {
            selectedGroups -= group
            allItems.filter { group in TypeGrouping.matchingGroups(it) }
                .forEach { item -> itemSelectionOverrides[item.id] = false }
        }
        return rebuild()
    }

    fun setAllTypesSelected(selected: Boolean): ReviewScreenState {
        selectedGroups.clear()
        if (selected) {
            selectedGroups += TypeGrouping.refactorableGroups
            allItems.forEach { item ->
                if (item.safetyLevel != SafetyLevel.DO_NOT_TOUCH) {
                    itemSelectionOverrides[item.id] = true
                }
            }
        } else {
            allItems.forEach { item -> itemSelectionOverrides[item.id] = false }
        }
        return rebuild()
    }

    fun setAllActiveItemsSelected(selected: Boolean): ReviewScreenState {
        currentActiveItems().forEach { item ->
            if (item.safetyLevel != SafetyLevel.DO_NOT_TOUCH) {
                itemSelectionOverrides[item.id] = selected
            }
        }
        return rebuild()
    }

    fun setItemSelected(itemId: String, selected: Boolean): ReviewScreenState {
        itemSelectionOverrides[itemId] = selected
        return rebuild()
    }

    fun setManualName(itemId: String, name: String): ReviewScreenState {
        itemNameOverrides[itemId] = name
        return rebuild()
    }

    fun setGroupCanonicalName(groupKey: String, name: String): ReviewScreenState {
        groupNameOverrides[groupKey] = name
        return rebuild()
    }

    fun setShowPreviouslyUsedNames(show: Boolean): ReviewScreenState {
        showPreviouslyUsedNames = show
        return rebuild()
    }

    private fun rebuild(): ReviewScreenState {
        val activeItems = currentActiveItems()
        val baseItems = suggestionService.buildReviewItems(
            items = activeItems,
            registry = registry,
            existingNamesByType = existingNamesByType,
            showPreviouslyUsedNames = showPreviouslyUsedNames,
        )

        val mergedItems = baseItems.map { built ->
            val groupName = groupNameOverrides[built.groupKey]
            val itemName = itemNameOverrides[built.item.id]
            val applySelection = itemSelectionOverrides[built.item.id]
            val selectedName = when {
                !itemName.isNullOrBlank() -> itemName
                !groupName.isNullOrBlank() -> groupName
                else -> built.selectedNewName
            }
            val overrideApplied = when {
                !itemName.isNullOrBlank() -> !itemName.equals(groupName ?: built.canonicalNewName, ignoreCase = true)
                !groupName.isNullOrBlank() -> false
                else -> built.overrideApplied
            }
            built.copy(
                canonicalNewName = groupName ?: built.canonicalNewName,
                selectedNewName = selectedName,
                overrideApplied = overrideApplied,
                selectedSuggestionSource = when {
                    !itemName.isNullOrBlank() || !groupName.isNullOrBlank() -> SuggestionSource.MANUAL
                    else -> built.selectedSuggestionSource
                },
                applySelected = applySelection ?: built.applySelected,
            )
        }

        val groupInfos = TypeGrouping.orderedGroups.map { group ->
            val matchingItems = allItems.filter { group in TypeGrouping.matchingGroups(it) }
            GroupSelectionInfo(
                group = group,
                totalCount = matchingItems.size,
                selectedItemCount = matchingItems.count { item ->
                    mergedItems.firstOrNull { it.item.id == item.id }?.applySelected
                        ?: itemSelectionOverrides[item.id]
                        ?: false
                },
                active = group in selectedGroups,
            )
        }

        val selectableActiveItems = mergedItems.filter { it.item.safetyLevel != SafetyLevel.DO_NOT_TOUCH }
        return ReviewScreenState(
            reviewItems = mergedItems,
            groupInfos = groupInfos,
            selectedGroups = selectedGroups.toSet(),
            selectAllTypes = TypeGrouping.refactorableGroups.all { it in selectedGroups },
            selectAllItems = selectableActiveItems.isNotEmpty() && selectableActiveItems.all { it.applySelected },
            showPreviouslyUsedNames = showPreviouslyUsedNames,
        )
    }

    private fun currentActiveItems(): List<ScannedRefactorItem> =
        allItems.filter { TypeGrouping.isActive(it, selectedGroups) }

    private fun defaultSelectedGroups(items: List<ScannedRefactorItem>): Set<RefactorSelectionGroup> {
        val presentGroups = items.flatMap { TypeGrouping.matchingGroups(it) }.toSet()
        return TypeGrouping.refactorableGroups
            .filter {
                it !in setOf(
                    RefactorSelectionGroup.FEATURE_PACKAGE,
                    RefactorSelectionGroup.REVIEW_REQUIRED,
                ) && it in presentGroups
            }
            .toSet()
    }
}
