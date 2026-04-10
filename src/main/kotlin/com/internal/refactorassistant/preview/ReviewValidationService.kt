package com.internal.refactorassistant.preview

import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ReviewValidation
import com.internal.refactorassistant.model.UsedNameMetadata
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.rules.NamingRules
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class ReviewValidationService(
    private val existingNamesByType: Map<RefactorItemType, Set<String>>,
    private val registry: UsedNamesRegistry,
) {
    fun usedMetadata(type: RefactorItemType, candidateName: String): UsedNameMetadata {
        val entry = registry.namesByType[type]
            .orEmpty()
            .firstOrNull { it.name.equals(candidateName, ignoreCase = true) }
        return if (entry == null) {
            UsedNameMetadata(false, null, null)
        } else {
            UsedNameMetadata(
                usedBefore = true,
                lastUsedVersion = entry.lastUsedVersion,
                lastUsedTimestamp = entry.lastUsedTimestamp,
            )
        }
    }

    fun validate(item: ReviewItemState, allItems: List<ReviewItemState>): ReviewValidation {
        val warnings = linkedSetOf<String>()
        if (item.warning.isNotBlank()) {
            warnings += item.warning
        }
        if (!item.applySelected) {
            return ReviewValidation(blocked = false, warnings = warnings.toList())
        }

        var blocked = false
        val selectedNewName = item.selectedNewName.trim()
        if (item.item.safetyLevel.name == "DO_NOT_TOUCH") {
            blocked = true
            warnings += "This item is marked DO_NOT_TOUCH."
        }
        if (item.item.type == RefactorItemType.PACKAGE_CHILD) {
            blocked = true
            warnings += "Package rename apply is disabled in this milestone."
        }
        if (selectedNewName.isBlank()) {
            blocked = true
            warnings += "Selected new name is required."
        }
        if (selectedNewName == item.item.oldName) {
            blocked = true
            warnings += "Selected new name must be different from the old name."
        }
        if (selectedNewName.isNotBlank() && !NamingRules.isValidName(item.item.type, selectedNewName)) {
            blocked = true
            warnings += "Selected new name is invalid for ${item.item.type.name.lowercase(Locale.US)}."
        }
        if (selectedNewName.isNotBlank() && clashesWithExisting(item, selectedNewName)) {
            blocked = true
            warnings += "Selected new name already exists in the scanned project."
        }
        if (selectedNewName.isNotBlank() && targetPathAlreadyExists(item, selectedNewName)) {
            blocked = true
            warnings += "A file or package already exists at the target path."
        }
        if (selectedNewName.isNotBlank() && duplicatesAnotherSelection(item, allItems, selectedNewName)) {
            blocked = true
            warnings += "Another selected item already uses the same new name."
        }

        val usedMetadata = usedMetadata(item.item.type, selectedNewName)
        if (usedMetadata.usedBefore) {
            warnings += buildString {
                append("This name was used in a previous refactor version")
                usedMetadata.lastUsedVersion?.let { append(" ($it)") }
                append(".")
            }
        }

        return ReviewValidation(blocked = blocked, warnings = warnings.toList())
    }

    private fun clashesWithExisting(item: ReviewItemState, selectedNewName: String): Boolean {
        val existingNames = existingNamesByType[item.item.type].orEmpty()
        return existingNames.any { it == selectedNewName && it != item.item.oldName }
    }

    private fun duplicatesAnotherSelection(
        item: ReviewItemState,
        allItems: List<ReviewItemState>,
        selectedNewName: String,
    ): Boolean {
        return allItems.any { other ->
            other.item.id != item.item.id &&
                other.applySelected &&
                other.item.type == item.item.type &&
                other.selectedNewName.trim().equals(selectedNewName, ignoreCase = true)
        }
    }

    private fun targetPathAlreadyExists(item: ReviewItemState, selectedNewName: String): Boolean {
        val currentPath = runCatching { Path.of(item.item.absolutePath) }.getOrNull() ?: return false
        return when (item.item.type) {
            RefactorItemType.KOTLIN_FILE -> Files.exists(currentPath.resolveSibling("$selectedNewName.kt"))
            RefactorItemType.LAYOUT,
            RefactorItemType.DRAWABLE,
            -> {
                val extension = currentPath.fileName.toString().substringAfterLast('.', "")
                val suffix = if (extension.isBlank()) "" else ".$extension"
                Files.exists(currentPath.resolveSibling(selectedNewName + suffix))
            }

            RefactorItemType.PACKAGE_CHILD -> {
                val sourceRootPath = item.item.details.sourceRootPath ?: return false
                Files.exists(Path.of(sourceRootPath, selectedNewName.replace('.', '/')))
            }

            else -> false
        }
    }
}
