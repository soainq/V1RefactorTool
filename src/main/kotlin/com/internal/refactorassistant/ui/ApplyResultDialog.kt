package com.internal.refactorassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.internal.refactorassistant.model.ApplyResult
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class ApplyResultDialog(
    project: Project,
    private val result: ApplyResult,
) : DialogWrapper(project, true) {
    init {
        title = "Reskin Refactor - Result"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val details = buildString {
            appendLine("Applied: ${result.appliedCount}")
            appendLine("Skipped: ${result.skippedCount}")
            appendLine("Failed: ${result.failedCount}")
            appendLine("Session log: ${result.sessionLogPath}")
            if (result.warnings.isNotEmpty()) {
                appendLine()
                appendLine("Warnings:")
                result.warnings.forEach { appendLine("- $it") }
            }
            if (result.errors.isNotEmpty()) {
                appendLine()
                appendLine("Errors:")
                result.errors.forEach { appendLine("- $it") }
            }
        }

        val textArea = JBTextArea(details).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        return JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(760, 420)
            add(JBLabel("Refactor execution summary"), BorderLayout.NORTH)
            add(JBScrollPane(textArea), BorderLayout.CENTER)
        }
    }
}
