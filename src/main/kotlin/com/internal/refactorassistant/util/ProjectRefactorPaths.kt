package com.internal.refactorassistant.util

import com.internal.refactorassistant.model.ProjectRefactorFiles
import java.nio.file.Path

object ProjectRefactorPaths {
    fun resolve(projectBasePath: String): ProjectRefactorFiles {
        val root = Path.of(projectBasePath).resolve(".project-refactor")
        return ProjectRefactorFiles(
            rootDirectory = root.toString(),
            historyFile = root.resolve("history.json").toString(),
            usedNamesFile = root.resolve("used-names.json").toString(),
            sessionsDirectory = root.resolve("sessions").toString(),
        )
    }
}
