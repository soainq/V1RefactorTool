package com.internal.refactorassistant.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.DialogWrapper
import com.internal.refactorassistant.executor.RefactorExecutor
import com.internal.refactorassistant.model.ExportFormat
import com.internal.refactorassistant.model.RefactorRequest
import com.internal.refactorassistant.model.RefactorPlan
import com.internal.refactorassistant.planner.RefactorPlanner
import com.internal.refactorassistant.report.ReportExporter
import com.internal.refactorassistant.report.ReportRenderer
import com.internal.refactorassistant.rules.NamingRuleEngine
import com.internal.refactorassistant.scan.ProjectStructureScanner
import com.internal.refactorassistant.settings.InternalRefactorAssistantSettingsService
import com.internal.refactorassistant.ui.ExecutionReportDialog
import com.internal.refactorassistant.ui.RefactorPlanPreviewDialog
import com.internal.refactorassistant.ui.RefactorRequestDialog
import com.internal.refactorassistant.util.NotificationUtil
import com.internal.refactorassistant.util.ParsingUtil

class DuplicateTemplateProjectAction : AnAction() {
    private val logger = Logger.getInstance(DuplicateTemplateProjectAction::class.java)

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = InternalRefactorAssistantSettingsService.getInstance()
        val moduleNames = ModuleManager.getInstance(project).modules.map { it.name }.ifEmpty { listOf("project") }
        val requestDialog = RefactorRequestDialog(project, settings.defaults(), moduleNames)
        if (!requestDialog.showAndGet()) {
            return
        }

        val request = requestDialog.toRequest(settings.defaults().customNamingRules)
        settings.rememberRequest(request)
        settings.updateDefaultOptions(request.options)

        generatePlan(project, request)
    }

    private fun generatePlan(project: Project, request: RefactorRequest) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generate Refactor Plan", true) {
            private lateinit var plan: RefactorPlan
            private lateinit var planner: RefactorPlanner
            private var errorMessage: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                runCatching {
                    indicator.text = "Parsing synonyms and naming rules"
                    indicator.fraction = 0.1
                    val synonyms = ParsingUtil.parseSynonymDictionary(request.synonymDictionaryText)

                    indicator.text = "Scanning project structure"
                    indicator.fraction = 0.35
                    val scanResult = ProjectStructureScanner(project).scan(request)

                    indicator.text = "Building refactor plan"
                    indicator.fraction = 0.75
                    planner = RefactorPlanner(NamingRuleEngine(request, synonyms))
                    plan = planner.buildPlan(request, scanResult)
                    indicator.fraction = 1.0
                }.onFailure { throwable ->
                    logger.warn("Failed to generate plan", throwable)
                    errorMessage = throwable.message ?: "Failed to generate refactor plan."
                }
            }

            override fun onSuccess() {
                if (errorMessage != null) {
                    Messages.showErrorDialog(project, errorMessage.orEmpty(), "Refactor Plan Failed")
                    return
                }
                val preview = RefactorPlanPreviewDialog(
                    project = project,
                    plan = plan,
                    previewRows = planner.previewRows(plan),
                    reportExporter = ReportExporter(project),
                )
                preview.show()
                if (!request.options.dryRunOnly && preview.exitCode == DialogWrapper.OK_EXIT_CODE) {
                    applyPlan(project, plan)
                }
            }
        })
    }

    private fun applyPlan(project: Project, plan: RefactorPlan) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Apply Refactor Plan", true) {
            private val exporter = ReportExporter(project)
            private lateinit var reportMarkdown: String
            private var errorMessage: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                runCatching {
                    indicator.text = "Applying refactor phases"
                    indicator.fraction = 0.7
                    val report = RefactorExecutor(project).execute(plan)

                    indicator.text = "Writing reports"
                    indicator.fraction = 0.9
                    val jsonPath = exporter.exportExecutionReport(report, ExportFormat.JSON)
                    val markdownPath = exporter.exportExecutionReport(report, ExportFormat.MARKDOWN)
                    reportMarkdown = ReportRenderer.renderExecutionReport(report, ExportFormat.MARKDOWN) +
                        "\n\nSaved reports:\n- ${jsonPath.toAbsolutePath()}\n- ${markdownPath.toAbsolutePath()}\n"
                    indicator.fraction = 1.0
                }.onFailure { throwable ->
                    logger.warn("Failed to apply plan", throwable)
                    errorMessage = throwable.message ?: "Failed to apply refactor plan."
                }
            }

            override fun onSuccess() {
                if (errorMessage != null) {
                    Messages.showErrorDialog(project, errorMessage.orEmpty(), "Refactor Apply Failed")
                    return
                }
                NotificationUtil.info(project, "Refactor completed. Reports were written to .internal-refactor-assistant/reports.")
                ExecutionReportDialog(project, reportMarkdown).show()
            }
        })
    }
}
