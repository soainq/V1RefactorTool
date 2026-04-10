package com.internal.refactorassistant.test

import com.internal.refactorassistant.history.HistoryRepository
import com.internal.refactorassistant.model.HistoryEntry
import com.internal.refactorassistant.model.HistoryFile
import com.internal.refactorassistant.model.ProjectRefactorFiles
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.RefactorSelectionGroup
import com.internal.refactorassistant.model.SessionMode
import com.internal.refactorassistant.model.SessionRecord
import com.internal.refactorassistant.model.SessionSummary
import com.internal.refactorassistant.model.UsedNamesRegistry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryRepositoryTest {
    @Test
    fun `load returns empty state when files do not exist`() {
        val root = Files.createTempDirectory("history-load")
        val repository = HistoryRepository()

        val result = repository.load(projectFiles(root))

        assertEquals(HistoryFile(), result.history)
        assertEquals(UsedNamesRegistry(), result.registry)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `save apply result writes history and used name registry`() {
        val root = Files.createTempDirectory("history-save")
        val repository = HistoryRepository()
        val files = projectFiles(root)

        repository.saveApplyResult(
            projectFiles = files,
            record = sessionRecord(),
            appliedEntries = listOf(
                HistoryEntry(
                    type = RefactorItemType.ACTIVITY,
                    oldName = "MainActivity",
                    newName = "HomeActivity",
                    timestamp = "2026-04-10T10:30:00+07:00",
                    versionLabel = "reskin_v1",
                    status = "applied",
                )
            ),
        )

        val loaded = repository.load(files)
        assertEquals(1, loaded.history.entries.size)
        assertEquals("HomeActivity", loaded.history.entries.first().newName)
        assertEquals("HomeActivity", loaded.registry.namesByType[RefactorItemType.ACTIVITY]?.first()?.name)
    }

    @Test
    fun `invalid history file returns warning and empty fallback`() {
        val root = Files.createTempDirectory("history-invalid")
        val files = projectFiles(root)
        Files.writeString(root.resolve("history.json"), "{ invalid json }")

        val result = HistoryRepository().load(files)

        assertTrue(result.warnings.any { it.contains("history.json is invalid") })
        assertEquals(emptyList(), result.history.entries)
    }

    private fun projectFiles(root: java.nio.file.Path): ProjectRefactorFiles = ProjectRefactorFiles(
        rootDirectory = root.toString(),
        historyFile = root.resolve("history.json").toString(),
        usedNamesFile = root.resolve("used-names.json").toString(),
        sessionsDirectory = root.resolve("sessions").toString(),
    )

    private fun sessionRecord(): SessionRecord = SessionRecord(
        sessionId = "session-1",
        timestamp = "2026-04-10T10:30:00+07:00",
        mode = SessionMode.APPLY,
        versionLabel = "reskin_v1",
        userNote = "",
        selectedTypes = listOf(RefactorSelectionGroup.ACTIVITY),
        selectedItemCount = 1,
        summary = SessionSummary(1, 1, 0, 0, 1, 0),
        items = emptyList(),
    )
}
