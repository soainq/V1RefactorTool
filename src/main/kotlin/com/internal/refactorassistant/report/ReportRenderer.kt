package com.internal.refactorassistant.report

import com.internal.refactorassistant.model.ExecutionReport
import com.internal.refactorassistant.model.ExportFormat
import com.internal.refactorassistant.model.PhaseStatus
import com.internal.refactorassistant.model.RefactorPlan
import com.internal.refactorassistant.util.ParsingUtil

object ReportRenderer {
    fun renderPlan(plan: RefactorPlan, format: ExportFormat): String = when (format) {
        ExportFormat.JSON -> ParsingUtil.planToJson(plan)
        ExportFormat.MARKDOWN -> renderPlanMarkdown(plan)
    }

    fun renderExecutionReport(report: ExecutionReport, format: ExportFormat): String = when (format) {
        ExportFormat.JSON -> ParsingUtil.toPrettyJson(report)
        ExportFormat.MARKDOWN -> renderExecutionMarkdown(report)
    }

    fun renderPlanMarkdown(plan: RefactorPlan): String = buildString {
        appendLine("# Refactor Plan")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Package operations: ${plan.counts.packageOperations}")
        appendLine("- Source operations: ${plan.counts.sourceOperations}")
        appendLine("- Resource operations: ${plan.counts.resourceOperations}")
        appendLine("- Reference operations: ${plan.counts.referenceOperations}")
        appendLine("- Affected files: ${plan.counts.affectedFiles}")
        appendLine("- Affected references: ${plan.counts.affectedReferences}")
        appendLine("- Warnings: ${plan.counts.warnings}")
        appendLine("- Conflicts: ${plan.counts.conflicts}")
        appendLine()
        appendLine("## Changes")
        appendLine()
        appendLine("| Category | Type | Before | After | Module | References |")
        appendLine("| --- | --- | --- | --- | --- | ---: |")
        fun row(category: String, type: String, before: String, after: String, module: String, references: Int) {
            appendLine("| $category | $type | `$before` | `$after` | $module | $references |")
        }
        plan.packageOperations.forEach { row(it.category, it.itemType, it.before, it.after, it.moduleName, it.affectedReferenceCount) }
        plan.sourceOperations.forEach { row(it.category, it.itemType, it.before, it.after, it.moduleName, it.affectedReferenceCount) }
        plan.resourceOperations.forEach { row(it.category, it.itemType, it.before, it.after, it.moduleName, it.affectedReferenceCount) }
        appendLine()
        appendLine("## Warnings")
        appendLine()
        if (plan.warnings.isEmpty()) {
            appendLine("- None")
        } else {
            plan.warnings.forEach { appendLine("- ${it.code}: ${it.message}") }
        }
        appendLine()
        appendLine("## Conflicts")
        appendLine()
        if (plan.conflicts.isEmpty()) {
            appendLine("- None")
        } else {
            plan.conflicts.forEach { appendLine("- ${it.code}: ${it.message}") }
        }
    }

    fun renderExecutionMarkdown(report: ExecutionReport): String = buildString {
        appendLine("# Refactor Execution Report")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Dry-run: ${report.dryRun}")
        appendLine("- Errors: ${report.hasErrors}")
        appendLine("- Files changed: ${report.filesChanged.size}")
        appendLine("- Git commit: ${report.gitResult.commitHash ?: report.gitResult.message.ifBlank { "not created" }}")
        appendLine("- Build verification: ${report.buildResult.summary}")
        appendLine()
        appendLine("## Phases")
        appendLine()
        appendLine("| Phase | Status | Attempted | Successful | Message |")
        appendLine("| --- | --- | ---: | ---: | --- |")
        report.phaseResults.forEach { phase ->
            val status = when (phase.status) {
                PhaseStatus.SUCCESS -> "success"
                PhaseStatus.FAILED -> "failed"
                PhaseStatus.SKIPPED -> "skipped"
            }
            appendLine("| ${phase.phase} | $status | ${phase.attemptedOperations} | ${phase.successfulOperations} | ${phase.message} |")
        }
        appendLine()
        appendLine("## Warnings")
        appendLine()
        if (report.warnings.isEmpty()) {
            appendLine("- None")
        } else {
            report.warnings.forEach { appendLine("- ${it.code}: ${it.message}") }
        }
        appendLine()
        appendLine("## Conflicts")
        appendLine()
        if (report.conflicts.isEmpty()) {
            appendLine("- None")
        } else {
            report.conflicts.forEach { appendLine("- ${it.code}: ${it.message}") }
        }
        appendLine()
        appendLine("## Files Changed")
        appendLine()
        if (report.filesChanged.isEmpty()) {
            appendLine("- None")
        } else {
            report.filesChanged.sorted().forEach { appendLine("- `$it`") }
        }
    }
}
