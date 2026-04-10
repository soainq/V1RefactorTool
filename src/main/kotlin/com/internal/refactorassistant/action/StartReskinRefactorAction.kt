package com.internal.refactorassistant.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.internal.refactorassistant.classify.ItemClassifier
import com.internal.refactorassistant.executor.FilesystemApplyEngine
import com.internal.refactorassistant.executor.IdePostProcessor
import com.internal.refactorassistant.history.HistoryRepository
import com.internal.refactorassistant.model.ApplyExecutionOutcome
import com.internal.refactorassistant.model.ApplyResult
import com.internal.refactorassistant.model.ProjectScanResult
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ScanSettings
import com.internal.refactorassistant.model.SessionItemResult
import com.internal.refactorassistant.preview.PreviewBuilder
import com.internal.refactorassistant.preview.ReviewValidationService
import com.internal.refactorassistant.report.SessionRecordFactory
import com.internal.refactorassistant.scan.ProjectScanner
import com.internal.refactorassistant.selection.ReviewSelectionCoordinator
import com.internal.refactorassistant.ui.ApplyResultDialog
import com.internal.refactorassistant.ui.PreviewPlanDialog
import com.internal.refactorassistant.ui.ScanSettingsDialog
import com.internal.refactorassistant.ui.SuggestionReviewDialog
import com.internal.refactorassistant.util.ProjectRefactorPaths

class StartReskinRefactorAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) {
            Messages.showErrorDialog(project, "The project base path is not available.", "Reskin Refactor")
            return
        }

        val projectFiles = ProjectRefactorPaths.resolve(basePath)
        val historyRepository = HistoryRepository()
        val loadState = historyRepository.load(projectFiles)
        var initialSettings: ScanSettings? = null
        while (true) {
            val scanDialog = ScanSettingsDialog(
                project = project,
                moduleNames = scanModuleNames(project),
                loadWarnings = loadState.warnings,
                initialSettings = initialSettings,
            )
            if (!scanDialog.showAndGet()) {
                return
            }

            val settings = scanDialog.toScanSettings()
            initialSettings = settings
            val scanResult = runWithProgress(project, "Scanning project for refactor candidates") {
                ItemClassifier().classify(ProjectScanner().scan(project, settings))
            }
            if (scanResult.items.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No supported refactor candidates were found for the selected scan options.",
                    "Reskin Refactor",
                )
                continue
            }

            val coordinator = ReviewSelectionCoordinator(
                allItems = scanResult.items,
                registry = loadState.registry,
                existingNamesByType = scanResult.existingNamesByType,
            )
            var reviewState = coordinator.state()
            while (true) {
                val reviewDialog = SuggestionReviewDialog(
                    project = project,
                    scanResult = scanResult,
                    coordinator = coordinator,
                    initialState = reviewState,
                    registry = loadState.registry,
                    topWarnings = loadState.warnings,
                )
                when (reviewDialog.showAndGetChoice()) {
                    SuggestionReviewDialog.Choice.CANCEL -> return
                    SuggestionReviewDialog.Choice.BACK -> break
                    SuggestionReviewDialog.Choice.PREVIEW -> Unit
                }

                reviewState = coordinator.state()
                val reviewItems = reviewState.reviewItems
                val selectedGroups = reviewState.selectedGroups
                val validator = ReviewValidationService(scanResult.existingNamesByType, loadState.registry)
                val previewPlan = PreviewBuilder(validator).build(reviewItems)
                val previewSession = SessionRecordFactory.preview(settings, selectedGroups, reviewItems, previewPlan)
                val previewSessionPath = runCatching { historyRepository.savePreviewSession(projectFiles, previewSession) }
                    .getOrElse { error ->
                        Messages.showWarningDialog(
                            project,
                            "Preview log could not be saved: ${error.message}",
                            "Reskin Refactor",
                        )
                        projectFiles.sessionsDirectory
                    }

                val previewDialog = PreviewPlanDialog(
                    project = project,
                    plan = previewPlan,
                    previewSessionPath = previewSessionPath,
                )
                when (previewDialog.showAndGetChoice()) {
                    PreviewPlanDialog.Choice.CANCEL -> return
                    PreviewPlanDialog.Choice.BACK -> continue
                    PreviewPlanDialog.Choice.APPLY -> {
                        val outcome = applyChanges(project, scanResult, reviewItems, previewPlan, settings, validator)
                        val applySession = SessionRecordFactory.apply(settings, selectedGroups, reviewItems, outcome)
                        val sessionLogPath = runCatching {
                            historyRepository.saveApplyResult(projectFiles, applySession, outcome.historyEntries)
                        }.getOrElse { error ->
                            Messages.showWarningDialog(
                                project,
                                "Apply session log could not be saved: ${error.message}",
                                "Reskin Refactor",
                            )
                            projectFiles.sessionsDirectory
                        }
                        ApplyResultDialog(
                            project = project,
                            result = ApplyResult(
                                appliedCount = outcome.itemResults.values.count { it == SessionItemResult.APPLIED },
                                skippedCount = outcome.itemResults.values.count { it == SessionItemResult.SKIPPED || it == SessionItemResult.BLOCKED },
                                failedCount = outcome.itemResults.values.count { it == SessionItemResult.FAILED },
                                warnings = outcome.warnings,
                                errors = outcome.errors,
                                changedFiles = outcome.changedFiles,
                                sessionLogPath = sessionLogPath,
                            ),
                        ).show()
                        return
                    }
                }
            }
        }
    }

    private fun applyChanges(
        project: Project,
        scanResult: ProjectScanResult,
        reviewItems: List<ReviewItemState>,
        previewPlan: com.internal.refactorassistant.model.PreviewPlan,
        settings: com.internal.refactorassistant.model.ScanSettings,
        validator: ReviewValidationService,
    ): ApplyExecutionOutcome {
        val engine = FilesystemApplyEngine(validator)
        val outcome = runWithProgress(project, "Applying selected refactor items") {
            var result: ApplyExecutionOutcome? = null
            WriteCommandAction.runWriteCommandAction(project) {
                result = engine.apply(
                    reviewItems = reviewItems,
                    previewPlan = previewPlan,
                    referenceFiles = scanResult.referenceFiles,
                    settings = settings,
                )
            }
            result ?: error("Apply engine did not produce an outcome.")
        }

        runWithProgress(project, "Reformatting changed files") {
            IdePostProcessor().reformat(project, outcome.changedFiles)
        }
        return outcome
    }

    private fun scanModuleNames(project: Project): List<String> =
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.map { it.name }.sorted()

    private fun <T> runWithProgress(
        project: Project,
        title: String,
        action: () -> T,
    ): T = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        object : ThrowableComputable<T, RuntimeException> {
            override fun compute(): T = action()
        },
        title,
        true,
        project,
    )
}
