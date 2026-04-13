package com.internal.refactorassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.internal.refactorassistant.model.ApplyResult
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class ApplyResultDialog(
    project: Project,
    private val result: ApplyResult,
) : DialogWrapper(project, true) {
    private val tableModel = DefaultTableModel(DEFAULT_COLUMNS.toTypedArray(), 0)
    private val table = JBTable(tableModel).apply {
        autoCreateRowSorter = true
        preferredScrollableViewportSize = Dimension(1280, 460)
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }
    private val detailArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        text = "Select a row to see result details."
    }

    init {
        title = "Reskin Refactor - Result"
        setOKButtonText("Close")
        result.rows.forEach { row ->
            tableModel.addRow(
                arrayOf(
                    row.type.name,
                    row.before,
                    row.after,
                    row.finalStatus,
                    row.reason,
                    row.path,
                )
            )
        }
        table.selectionModel.addListSelectionListener {
            val modelRow = table.selectedRow.takeIf { it >= 0 }?.let(table::convertRowIndexToModel) ?: return@addListSelectionListener
            val row = result.rows[modelRow]
            detailArea.text = buildString {
                appendLine("Type: ${row.type.name}")
                appendLine("Before: ${row.before}")
                appendLine("After: ${row.after}")
                appendLine("Final Status: ${row.finalStatus}")
                appendLine("Reason: ${row.reason}")
                appendLine("Path: ${row.path}")
            }
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val summary = JBLabel(
            "<html><b>Success:</b> ${result.appliedCount}<br>" +
                "<b>Failed:</b> ${result.failedCount}<br>" +
                "<b>Skipped:</b> ${result.skippedCount}<br>" +
                "<b>Blocked:</b> ${result.blockedCount}<br>" +
                "<b>Session log:</b> ${result.sessionLogPath}</html>"
        )
        val diagnostics = JBTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            text = buildString {
                if (result.warnings.isNotEmpty()) {
                    appendLine("Warnings:")
                    result.warnings.forEach { appendLine("- $it") }
                }
                if (result.errors.isNotEmpty()) {
                    if (isNotBlank()) appendLine()
                    appendLine("Errors:")
                    result.errors.forEach { appendLine("- $it") }
                }
                if (isBlank()) {
                    append("No warnings or errors.")
                }
            }
        }
        return JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(1320, 760)
            add(summary, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(JBScrollPane(detailArea), BorderLayout.SOUTH)
            add(JBScrollPane(diagnostics), BorderLayout.EAST)
        }
    }

    companion object {
        val DEFAULT_COLUMNS = listOf("Type", "Before", "After", "Final Status", "Reason", "Path")
    }
}
