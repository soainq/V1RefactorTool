package com.internal.refactorassistant.history

import com.internal.refactorassistant.model.HistoryEntry
import com.internal.refactorassistant.model.HistoryFile
import com.internal.refactorassistant.model.LoadStateResult
import com.internal.refactorassistant.model.ProjectRefactorFiles
import com.internal.refactorassistant.model.SessionRecord
import com.internal.refactorassistant.model.UsedNameEntry
import com.internal.refactorassistant.model.UsedNamesRegistry
import java.nio.file.Files
import java.nio.file.Path

class HistoryRepository {
    fun load(projectFiles: ProjectRefactorFiles): LoadStateResult {
        val warnings = mutableListOf<String>()
        val root = Path.of(projectFiles.rootDirectory)
        runCatching { Files.createDirectories(root) }
            .onFailure { warnings += "Could not create .project-refactor directory: ${it.message}" }

        val historyPath = Path.of(projectFiles.historyFile)
        val registryPath = Path.of(projectFiles.usedNamesFile)

        val history = runCatching {
            if (Files.exists(historyPath)) JsonStorage.read<HistoryFile>(historyPath) else HistoryFile()
        }
            .getOrElse {
                warnings += "history.json is invalid. A new empty history will be used."
                HistoryFile()
            }
        val registry = runCatching {
            if (Files.exists(registryPath)) JsonStorage.read<UsedNamesRegistry>(registryPath) else UsedNamesRegistry()
        }
            .getOrElse {
                warnings += "used-names.json is invalid. A new empty registry will be used."
                UsedNamesRegistry()
            }

        return LoadStateResult(history = history, registry = registry, warnings = warnings)
    }

    fun savePreviewSession(projectFiles: ProjectRefactorFiles, record: SessionRecord): String {
        val sessionPath = Path.of(projectFiles.sessionsDirectory).resolve("${record.sessionId}.json")
        JsonStorage.write(sessionPath, record)
        return sessionPath.toString()
    }

    fun saveApplyResult(
        projectFiles: ProjectRefactorFiles,
        record: SessionRecord,
        appliedEntries: List<HistoryEntry>,
    ): String {
        val sessionPath = Path.of(projectFiles.sessionsDirectory).resolve("${record.sessionId}.json")
        JsonStorage.write(sessionPath, record)

        val historyPath = Path.of(projectFiles.historyFile)
        val existingHistory = JsonStorage.readOrDefault(historyPath, HistoryFile())
        JsonStorage.write(historyPath, HistoryFile(entries = existingHistory.entries + appliedEntries))

        val registryPath = Path.of(projectFiles.usedNamesFile)
        val existingRegistry = JsonStorage.readOrDefault(registryPath, UsedNamesRegistry())
        val merged = mergeRegistry(existingRegistry, appliedEntries)
        JsonStorage.write(registryPath, merged)

        return sessionPath.toString()
    }

    private fun mergeRegistry(
        existingRegistry: UsedNamesRegistry,
        appliedEntries: List<HistoryEntry>,
    ): UsedNamesRegistry {
        val mutable = existingRegistry.namesByType.mapValues { (_, value) -> value.toMutableList() }.toMutableMap()
        appliedEntries.forEach { entry ->
            val bucket = mutable.getOrPut(entry.type) { mutableListOf() }
            bucket.removeAll { it.name == entry.newName }
            bucket += UsedNameEntry(
                name = entry.newName,
                lastUsedVersion = entry.versionLabel,
                lastUsedTimestamp = entry.timestamp,
            )
        }
        return UsedNamesRegistry(namesByType = mutable.mapValues { (_, value) -> value.sortedBy { it.name } })
    }
}
