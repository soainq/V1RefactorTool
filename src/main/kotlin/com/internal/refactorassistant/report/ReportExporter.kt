package com.internal.refactorassistant.report

import com.intellij.openapi.project.Project
import com.internal.refactorassistant.model.ExecutionReport
import com.internal.refactorassistant.model.ExportFormat
import com.internal.refactorassistant.model.RefactorPlan
import com.internal.refactorassistant.util.FileUtilEx
import java.nio.file.Path

class ReportExporter(
    private val project: Project,
) {
    fun exportPlan(plan: RefactorPlan, format: ExportFormat): Path {
        val base = baseDirectory().resolve("preview")
        val extension = if (format == ExportFormat.JSON) "json" else "md"
        val path = FileUtilEx.createReportFile(base, "refactor-plan", extension)
        return FileUtilEx.writeUtf8(path, ReportRenderer.renderPlan(plan, format))
    }

    fun exportExecutionReport(report: ExecutionReport, format: ExportFormat): Path {
        val base = baseDirectory().resolve("reports")
        val extension = if (format == ExportFormat.JSON) "json" else "md"
        val path = FileUtilEx.createReportFile(base, "refactor-report", extension)
        return FileUtilEx.writeUtf8(path, ReportRenderer.renderExecutionReport(report, format))
    }

    private fun baseDirectory(): Path = Path.of(project.basePath ?: ".").resolve(".internal-refactor-assistant")
}
