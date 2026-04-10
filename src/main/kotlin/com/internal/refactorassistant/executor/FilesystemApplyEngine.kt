package com.internal.refactorassistant.executor

import com.internal.refactorassistant.model.ApplyExecutionOutcome
import com.internal.refactorassistant.model.HistoryEntry
import com.internal.refactorassistant.model.PreviewPlan
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ScanSettings
import com.internal.refactorassistant.model.SessionItemResult
import com.internal.refactorassistant.preview.ReviewValidationService
import com.internal.refactorassistant.util.TimeUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

class FilesystemApplyEngine(
    private val validator: ReviewValidationService,
) {
    fun apply(
        reviewItems: List<ReviewItemState>,
        previewPlan: PreviewPlan,
        referenceFiles: List<String>,
        settings: ScanSettings,
    ): ApplyExecutionOutcome {
        val itemResults = linkedMapOf<String, SessionItemResult>()
        val warningsByItemId = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val changedFiles = linkedSetOf<String>()
        val additionalReferenceFiles = linkedSetOf<String>()
        val appliedEntries = mutableListOf<HistoryEntry>()

        val previewStatusById = previewPlan.rows.associateBy { row ->
            reviewItems.first { it.item.oldName == row.before && it.item.type == row.type && it.item.displayPath == row.path }.item.id
        }

        reviewItems.forEach { item ->
            val row = previewStatusById[item.item.id]
            when (row?.status) {
                "READY" -> itemResults[item.item.id] = SessionItemResult.PREVIEWED
                "BLOCKED" -> itemResults[item.item.id] = SessionItemResult.BLOCKED
                else -> itemResults[item.item.id] = SessionItemResult.SKIPPED
            }
            if (!row?.warning.isNullOrBlank()) {
                warningsByItemId[item.item.id] = row!!.warning
            }
        }

        val selectedItems = reviewItems.filter { item ->
            previewStatusById[item.item.id]?.status == "READY"
        }
        val eligibleItems = mutableListOf<ReviewItemState>()

        selectedItems.forEach { item ->
            val validation = validator.validate(item, reviewItems)
            if (validation.blocked) {
                itemResults[item.item.id] = SessionItemResult.BLOCKED
                warningsByItemId[item.item.id] = validation.warnings.joinToString(" ")
                warnings += validation.warnings
                return@forEach
            }

            val renameResult = renamePhysicalFileIfNeeded(item)
            if (renameResult.error != null) {
                itemResults[item.item.id] = SessionItemResult.FAILED
                warningsByItemId[item.item.id] = renameResult.error
                errors += "${item.item.oldName}: ${renameResult.error}"
                return@forEach
            }

            when (item.item.type) {
                RefactorItemType.PACKAGE_CHILD -> {
                    itemResults[item.item.id] = SessionItemResult.BLOCKED
                    val message = "Package rename apply is disabled in this milestone."
                    warningsByItemId[item.item.id] = message
                    warnings += message
                }

                else -> {
                    val changedPath = renameResult.changedPath ?: item.item.absolutePath
                    changedFiles += changedPath
                    additionalReferenceFiles += changedPath
                    eligibleItems += item
                }
            }
        }

        val replacements = buildTextReplacements(eligibleItems)
        (referenceFiles + additionalReferenceFiles)
            .distinct()
            .map(Path::of)
            .filter { Files.exists(it) && Files.isRegularFile(it) }
            .forEach { path ->
                val original = Files.readString(path, StandardCharsets.UTF_8)
                val updated = applyReplacements(original, replacements)
                if (updated != original) {
                    Files.writeString(path, updated, StandardCharsets.UTF_8)
                    changedFiles += path.toString()
                }
            }

        eligibleItems.forEach { item ->
            itemResults[item.item.id] = SessionItemResult.APPLIED
            appliedEntries += HistoryEntry(
                type = item.item.type,
                oldName = item.item.oldName,
                newName = item.selectedNewName.trim(),
                timestamp = TimeUtil.nowIso(),
                versionLabel = settings.versionLabel,
                status = "applied",
            )
        }

        return ApplyExecutionOutcome(
            itemResults = itemResults,
            warningsByItemId = warningsByItemId,
            historyEntries = appliedEntries,
            warnings = warnings.distinct(),
            errors = errors,
            changedFiles = changedFiles.toList().distinct().sorted(),
        )
    }

    private fun renamePhysicalFileIfNeeded(item: ReviewItemState): FileRenameResult {
        val currentPath = Path.of(item.item.absolutePath)
        if (!Files.exists(currentPath)) {
            return FileRenameResult(null, null)
        }

        val targetPath = when (item.item.type) {
            RefactorItemType.KOTLIN_FILE -> currentPath.resolveSibling("${item.selectedNewName.trim()}.kt")
            RefactorItemType.LAYOUT,
            RefactorItemType.DRAWABLE,
            -> {
                val extension = currentPath.fileName.toString().substringAfterLast('.', "")
                val suffix = if (extension.isBlank()) "" else ".$extension"
                currentPath.resolveSibling(item.selectedNewName.trim() + suffix)
            }

            else -> return FileRenameResult(null, null)
        }

        if (targetPath == currentPath) {
            return FileRenameResult(currentPath.toString(), null)
        }
        if (Files.exists(targetPath)) {
            return FileRenameResult(null, "Target file already exists: $targetPath")
        }

        val error = runCatching {
            Files.move(currentPath, targetPath, StandardCopyOption.ATOMIC_MOVE)
        }.fold(
            onSuccess = { null },
            onFailure = {
                runCatching {
                    Files.move(currentPath, targetPath)
                }.exceptionOrNull()?.message ?: it.message ?: "Unknown file rename error."
            },
        )
        return FileRenameResult(targetPath.toString(), error)
    }

    private fun buildTextReplacements(items: List<ReviewItemState>): List<Pair<Regex, String>> {
        return items.flatMap { item ->
            val oldName = item.item.oldName
            val newName = item.selectedNewName.trim()
            when (item.item.type) {
                RefactorItemType.ACTIVITY,
                RefactorItemType.FRAGMENT,
                RefactorItemType.VIEWMODEL,
                RefactorItemType.ADAPTER,
                RefactorItemType.KOTLIN_CLASS,
                -> listOf(exactTokenRegex(oldName) to newName)

                RefactorItemType.LAYOUT,
                RefactorItemType.DRAWABLE,
                RefactorItemType.STRING,
                RefactorItemType.DIMEN,
                -> listOf(exactTokenRegex(oldName) to newName)

                else -> emptyList()
            }
        }
    }

    private fun applyReplacements(
        original: String,
        replacements: List<Pair<Regex, String>>,
    ): String {
        var updated = original
        replacements.forEach { (regex, replacement) ->
            updated = regex.replace(updated, replacement)
        }
        return updated
    }

    private fun exactTokenRegex(value: String): Regex =
        Regex("(?<![A-Za-z0-9_])${Pattern.quote(value)}(?![A-Za-z0-9_])")

    private data class FileRenameResult(
        val changedPath: String?,
        val error: String?,
    )
}
