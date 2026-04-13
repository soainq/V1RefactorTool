package com.internal.refactorassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import javax.swing.JComponent

class ExecutionReportDialog(
    project: Project,
    summaryMarkdown: String,
) : DialogWrapper(project) {
    private val textArea = JBTextArea(summaryMarkdown).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        caretPosition = 0
    }

    init {
        title = "Refactor Execution Report"
        init()
    }

    override fun createCenterPanel(): JComponent = JBScrollPane(textArea).apply {
        preferredSize = Dimension(900, 500)
    }
}
