package com.internal.refactorassistant.executor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.internal.refactorassistant.model.ApplyExecutionOutcome
import com.internal.refactorassistant.model.ApplyProgressUpdate
import com.internal.refactorassistant.model.HistoryEntry
import com.internal.refactorassistant.model.PreviewPlan
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ScanSettings
import com.internal.refactorassistant.model.SessionItemResult
import com.internal.refactorassistant.preview.ReviewValidationService
import com.internal.refactorassistant.util.TimeUtil
import java.nio.charset.StandardCharsets
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.regex.Pattern

class FilesystemApplyEngine(
    private val validator: ReviewValidationService,
    private val project: Project? = null,
    private val progressReporter: ((ApplyProgressUpdate) -> Unit)? = null,
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
        val totalItems = reviewItems.size
        var processedItems = 0
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0

        val previewStatusById = previewPlan.rows.associateBy { row ->
            reviewItems.first { it.item.oldName == row.before && it.item.type == row.type && it.item.displayPath == row.path }.item.id
        }

        reviewItems.forEach { item ->
            val row = previewStatusById[item.item.id]
            when (row?.status) {
                "READY" -> itemResults[item.item.id] = SessionItemResult.PREVIEWED
                "BLOCKED" -> {
                    itemResults[item.item.id] = SessionItemResult.BLOCKED
                    skippedCount += 1
                }
                else -> {
                    itemResults[item.item.id] = SessionItemResult.SKIPPED
                    skippedCount += 1
                }
            }
            if (!row?.warning.isNullOrBlank()) {
                warningsByItemId[item.item.id] = row!!.warning
            }
        }

        processedItems = skippedCount

        val selectedItems = reviewItems.filter { item ->
            previewStatusById[item.item.id]?.status == "READY"
        }
        val eligibleItems = mutableListOf<ReviewItemState>()

        selectedItems.forEach { item ->
            reportProgress(
                totalItems = totalItems,
                processedItems = processedItems,
                successCount = successCount,
                failedCount = failedCount,
                skippedCount = skippedCount,
                currentItemLabel = "${item.item.oldName} -> ${item.selectedNewName.trim()}",
            )
            val validation = validator.validate(item, reviewItems)
            if (validation.blocked) {
                itemResults[item.item.id] = SessionItemResult.BLOCKED
                warningsByItemId[item.item.id] = validation.warnings.joinToString(" ")
                warnings += validation.warnings
                skippedCount += 1
                processedItems += 1
                reportProgress(totalItems, processedItems, successCount, failedCount, skippedCount, "${item.item.oldName} -> ${item.selectedNewName.trim()}")
                return@forEach
            }

            val renameResult = renamePhysicalFileIfNeeded(item)
            if (renameResult.error != null) {
                itemResults[item.item.id] = SessionItemResult.FAILED
                warningsByItemId[item.item.id] = renameResult.error
                errors += "${item.item.oldName}: ${renameResult.error}"
                failedCount += 1
                processedItems += 1
                reportProgress(totalItems, processedItems, successCount, failedCount, skippedCount, "${item.item.oldName} -> ${item.selectedNewName.trim()}")
                return@forEach
            }

            when (item.item.type) {
                RefactorItemType.PACKAGE_CHILD -> {
                    itemResults[item.item.id] = SessionItemResult.BLOCKED
                    val message = "Package rename apply is disabled in this milestone."
                    warningsByItemId[item.item.id] = message
                    warnings += message
                    skippedCount += 1
                    processedItems += 1
                    reportProgress(totalItems, processedItems, successCount, failedCount, skippedCount, "${item.item.oldName} -> ${item.selectedNewName.trim()}")
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
        val ownedPathToItemIds = eligibleItems
            .flatMap { item ->
                val paths = buildList {
                    add(item.item.absolutePath)
                    val renamedPath = resolvedPhysicalTargetPath(item)
                    if (renamedPath != null) add(renamedPath.toString())
                }.distinct()
                paths.map { path -> path to item.item.id }
            }
            .groupBy({ it.first }, { it.second })

        (referenceFiles + additionalReferenceFiles)
            .distinct()
            .map(Path::of)
            .filter { Files.exists(it) && Files.isRegularFile(it) }
            .forEach { path ->
                if (!isTextFile(path)) {
                    return@forEach
                }

                runCatching {
                    val original = Files.readString(path, StandardCharsets.UTF_8)
                    val updated = applyReplacements(original, replacements)
                    if (updated != original) {
                        writeUpdatedText(path, updated)
                        changedFiles += path.toString()
                    }
                }.onFailure { error ->
                    val message = buildReadFailureMessage(path, error)
                    val affectedItemIds = ownedPathToItemIds[path.toString()].orEmpty()
                    if (affectedItemIds.isNotEmpty()) {
                        affectedItemIds.forEach { itemId ->
                            itemResults[itemId] = SessionItemResult.FAILED
                            warningsByItemId[itemId] = message
                            failedCount += 1
                            processedItems += 1
                            reportProgress(
                                totalItems = totalItems,
                                processedItems = processedItems,
                                successCount = successCount,
                                failedCount = failedCount,
                                skippedCount = skippedCount,
                                currentItemLabel = path.fileName.toString(),
                            )
                        }
                    } else {
                        warnings += message
                    }
                    errors += message
                }
            }

        eligibleItems.forEach { item ->
            if (itemResults[item.item.id] == SessionItemResult.FAILED) return@forEach
            itemResults[item.item.id] = SessionItemResult.APPLIED
            successCount += 1
            processedItems += 1
            appliedEntries += HistoryEntry(
                type = item.item.type,
                oldName = item.item.oldName,
                newName = item.selectedNewName.trim(),
                timestamp = TimeUtil.nowIso(),
                versionLabel = settings.versionLabel,
                status = "applied",
            )
            reportProgress(totalItems, processedItems, successCount, failedCount, skippedCount, "${item.item.oldName} -> ${item.selectedNewName.trim()}")
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

    private fun reportProgress(
        totalItems: Int,
        processedItems: Int,
        successCount: Int,
        failedCount: Int,
        skippedCount: Int,
        currentItemLabel: String,
    ) {
        progressReporter?.invoke(
            ApplyProgressUpdate(
                totalItems = totalItems,
                processedItems = processedItems,
                successCount = successCount,
                failedCount = failedCount,
                skippedCount = skippedCount,
                currentItemLabel = currentItemLabel,
            )
        )
    }

    private fun renamePhysicalFileIfNeeded(item: ReviewItemState): FileRenameResult {
        val currentPath = Path.of(item.item.absolutePath)
        if (!Files.exists(currentPath)) {
            return FileRenameResult(null, null)
        }

        val targetPath = resolvedPhysicalTargetPath(item) ?: return FileRenameResult(null, null)

        if (targetPath == currentPath) {
            return FileRenameResult(currentPath.toString(), null)
        }
        if (Files.exists(targetPath)) {
            return FileRenameResult(null, "Target file already exists: $targetPath")
        }

        if (project != null) {
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(currentPath)
            if (virtualFile != null) {
                val fileDocumentManager = FileDocumentManager.getInstance()
                fileDocumentManager.getDocument(virtualFile)?.let(fileDocumentManager::saveDocument)
                val error = runCatching {
                    virtualFile.rename(this, targetPath.fileName.toString())
                }.exceptionOrNull()?.message
                if (error == null) {
                    return FileRenameResult(targetPath.toString(), null)
                }
            }
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

    private fun resolvedPhysicalTargetPath(item: ReviewItemState): Path? {
        val currentPath = Path.of(item.item.absolutePath)
        return when (item.item.type) {
            RefactorItemType.KOTLIN_FILE -> currentPath.resolveSibling("${item.selectedNewName.trim()}.kt")
            RefactorItemType.LAYOUT,
            RefactorItemType.DRAWABLE,
            -> {
                val extension = currentPath.fileName.toString().substringAfterLast('.', "")
                val suffix = if (extension.isBlank()) "" else ".$extension"
                currentPath.resolveSibling(item.selectedNewName.trim() + suffix)
            }

            else -> null
        }
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

    private fun writeUpdatedText(path: Path, updated: String) {
        val project = project
        if (project != null) {
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            if (virtualFile != null) {
                val fileDocumentManager = FileDocumentManager.getInstance()
                val psiDocumentManager = PsiDocumentManager.getInstance(project)
                val document = fileDocumentManager.getDocument(virtualFile)
                if (document != null) {
                    document.setText(updated)
                    psiDocumentManager.commitDocument(document)
                    fileDocumentManager.saveDocument(document)
                    return
                }
            }
        }
        Files.writeString(path, updated, StandardCharsets.UTF_8)
    }

    private fun isTextFile(path: Path): Boolean {
        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.US)
        return extension in textExtensions
    }

    private fun buildReadFailureMessage(path: Path, error: Throwable): String {
        val suffix = when (error) {
            is MalformedInputException -> "File is not valid UTF-8 text."
            else -> error.message ?: error::class.simpleName ?: "Unknown file processing error."
        }
        return "Failed to process ${path.fileName}: $suffix"
    }

    private data class FileRenameResult(
        val changedPath: String?,
        val error: String?,
    )

    companion object {
        private val textExtensions = setOf(
            "kt",
            "java",
            "xml",
            "gradle",
            "kts",
            "json",
            "txt",
            "md",
            "properties",
            "pro",
        )
    }
}
