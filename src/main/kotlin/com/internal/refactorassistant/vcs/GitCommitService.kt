package com.internal.refactorassistant.vcs

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.internal.refactorassistant.model.GitResult
import java.nio.file.Files
import java.nio.file.Path

class GitCommitService {
    private val logger = Logger.getInstance(GitCommitService::class.java)

    fun commitIfRequested(project: Project, enabled: Boolean, message: String): GitResult {
        if (!enabled) {
            return GitResult(enabled = false, attempted = false, committed = false, message = "Git commit disabled")
        }

        val projectPath = project.basePath?.let(Path::of)
            ?: return GitResult(enabled = true, attempted = false, committed = false, message = "Project has no base path")
        val repoRoot = findGitRoot(projectPath)
            ?: return GitResult(enabled = true, attempted = false, committed = false, message = "No git repository detected")

        val addResult = runGit(repoRoot, "add", "-A")
        if (addResult.exitCode != 0) {
            return GitResult(enabled = true, attempted = true, committed = false, message = addResult.stderr.ifBlank { "git add failed" })
        }

        val commitResult = runGit(repoRoot, "commit", "-m", message)
        if (commitResult.exitCode != 0) {
            val output = listOf(commitResult.stdout, commitResult.stderr).filter { it.isNotBlank() }.joinToString("\n")
            return GitResult(enabled = true, attempted = true, committed = false, message = output.ifBlank { "git commit failed" })
        }

        val revParse = runGit(repoRoot, "rev-parse", "HEAD")
        val hash = revParse.stdout.lineSequence().firstOrNull()?.trim()
        return GitResult(
            enabled = true,
            attempted = true,
            committed = true,
            commitHash = hash,
            message = "Created git commit ${hash ?: "<unknown>"}",
        )
    }

    private fun runGit(repoRoot: Path, vararg args: String): ProcessOutput {
        val commandLine = GeneralCommandLine("git", *args)
            .withWorkDirectory(repoRoot.toFile())
        val result = CapturingProcessHandler(commandLine).runProcess(60_000)
        logger.info("git ${args.joinToString(" ")} -> ${result.exitCode}")
        return ProcessOutput(result.exitCode, result.stdout, result.stderr)
    }

    private fun findGitRoot(start: Path): Path? {
        var current: Path? = start
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private data class ProcessOutput(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
