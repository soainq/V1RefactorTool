package com.internal.refactorassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.internal.refactorassistant.model.ExportFormat
import com.internal.refactorassistant.model.IssueSeverity
import com.internal.refactorassistant.model.PreviewRow
import com.internal.refactorassistant.model.RefactorPlan
import com.internal.refactorassistant.report.ReportExporter
import com.internal.refactorassistant.util.NotificationUtil
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.table.AbstractTableModel
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class RefactorPlanPreviewDialog(
    private val project: Project,
    private val plan: RefactorPlan,
    previewRows: List<PreviewRow>,
    private val reportExporter: ReportExporter,
) : DialogWrapper(project) {
    private val tableModel = PreviewTableModel(previewRows)
    private val table = JBTable(tableModel).apply {
        autoCreateRowSorter = true
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        preferredScrollableViewportSize = Dimension(920, 340)
    }
    private val issuesList = JBList(buildIssueLines()).apply {
        cellRenderer = object : ColoredListCellRenderer<String>() {
            override fun customizeCellRenderer(
                list: javax.swing.JList<out String>,
                value: String?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                val attributes = when {
                    value?.startsWith("ERROR") == true -> SimpleTextAttributes.ERROR_ATTRIBUTES
                    value?.startsWith("WARNING") == true -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                    else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(value.orEmpty(), attributes)
            }
        }
    }

    init {
        title = "Refactor Plan Preview"
        setOKButtonText(if (plan.request.options.dryRunOnly) "Preview Done" else "Apply")
        setCancelButtonText("Close")
        if (plan.conflicts.any { it.severity == IssueSeverity.ERROR } && !plan.request.options.dryRunOnly) {
            isOKActionEnabled = false
            setErrorText("Resolve plan conflicts before applying.")
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val splitter = JBSplitter(true, 0.72f)
        splitter.firstComponent = JBScrollPane(table)
        splitter.secondComponent = JBScrollPane(issuesList)
        return splitter
    }

    override fun createActions(): Array<Action> {
        val base = super.createActions().toMutableList()
        if (plan.request.options.dryRunOnly) {
            base.remove(okAction)
        }
        return base.toTypedArray()
    }

    override fun createLeftSideActions(): Array<Action> = arrayOf(
        object : DialogWrapperAction("Export JSON") {
            override fun doAction(e: ActionEvent?) {
                export(ExportFormat.JSON)
            }
        },
        object : DialogWrapperAction("Export Markdown") {
            override fun doAction(e: ActionEvent?) {
                export(ExportFormat.MARKDOWN)
            }
        },
    )

    private fun export(format: ExportFormat) {
        val path = reportExporter.exportPlan(plan, format)
        NotificationUtil.info(project, "Exported plan to ${path.toAbsolutePath()}")
        Messages.showInfoMessage(project, "Plan exported to:\n${path.toAbsolutePath()}", "Plan Exported")
    }

    private fun buildIssueLines(): List<String> {
        val lines = mutableListOf<String>()
        if (plan.conflicts.isEmpty() && plan.warnings.isEmpty()) {
            lines += "INFO: No warnings or conflicts."
        }
        plan.conflicts.forEach { lines += "ERROR: ${it.code} - ${it.message}" }
        plan.warnings.forEach { lines += "WARNING: ${it.code} - ${it.message}" }
        return lines
    }

    private class PreviewTableModel(
        private val rows: List<PreviewRow>,
    ) : AbstractTableModel() {
        private val columns = listOf("Category", "Type", "Before", "After", "Module", "Refs", "Path")

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.category
                1 -> row.type
                2 -> row.before
                3 -> row.after
                4 -> row.moduleName
                5 -> row.references
                6 -> row.path.orEmpty()
                else -> ""
            }
        }
    }
}
