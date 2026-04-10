package com.internal.refactorassistant.test

import com.internal.refactorassistant.model.ItemDetails
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ScanSettings
import com.internal.refactorassistant.model.ScannedRefactorItem
import com.internal.refactorassistant.model.SafetyLevel
import com.internal.refactorassistant.model.SuggestionCandidate
import com.internal.refactorassistant.model.UsedNameMetadata

object TestFixtures {
    fun scannedItem(
        id: String = "item-1",
        type: RefactorItemType,
        oldName: String,
        absolutePath: String = "C:/tmp/$oldName",
        safetyLevel: SafetyLevel = SafetyLevel.SAFE_AUTO,
        details: ItemDetails = ItemDetails(),
    ): ScannedRefactorItem = ScannedRefactorItem(
        id = id,
        type = type,
        oldName = oldName,
        displayPath = absolutePath,
        absolutePath = absolutePath,
        moduleName = "app",
        safetyLevel = safetyLevel,
        details = details,
    )

    fun reviewState(
        item: ScannedRefactorItem,
        selectedNewName: String,
        applySelected: Boolean = true,
        suggestions: List<String> = listOf(selectedNewName),
        warning: String = "",
    ): ReviewItemState = ReviewItemState(
        item = item,
        suggestions = suggestions.map {
            SuggestionCandidate(
                value = it,
                usedMetadata = UsedNameMetadata(false, null, null),
            )
        },
        selectedNewName = selectedNewName,
        applySelected = applySelected,
        status = "Ready",
        warning = warning,
    )

    fun settings(versionLabel: String = "reskin_v1"): ScanSettings = ScanSettings(
        selectedModules = listOf("app"),
        scanKotlinClassesFiles = true,
        scanActivities = true,
        scanFragments = true,
        scanViewModels = true,
        scanAdapters = true,
        scanFeaturePackages = true,
        scanLayouts = true,
        scanDrawables = true,
        scanStrings = true,
        scanDimens = true,
        versionLabel = versionLabel,
        note = "",
    )
}
