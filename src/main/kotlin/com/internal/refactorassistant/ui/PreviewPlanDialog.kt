package com.internal.refactorassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.internal.refactorassistant.model.PreviewPlan
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class PreviewPlanDialog(
    project: Project,
    private val plan: PreviewPlan,
    private val previewSessionPath: String,
) : DialogWrapper(project, true) {
    enum class Choice {
        APPLY,
        BACK,
        CANCEL,
    }

    private var choice: Choice = Choice.CANCEL
    private val applyAction = ApplyAction()
    private val backAction = BackAction()
    private val tableModel = DefaultTableModel(
        arrayOf("Type", "Before", "After", "Safety", "Status", "Warning", "Path"),
        0,
    )
    private val table = JBTable(tableModel).apply {
        autoCreateRowSorter = true
        preferredScrollableViewportSize = Dimension(1280, 520)
    }

    init {
        title = "Reskin Refactor - Preview"
        applyAction.isEnabled = plan.summary.selectedCount > 0
        plan.rows.sortedWith(compareBy({ it.type.name }, { it.before })).forEach { row ->
            tableModel.addRow(
                arrayOf(
                    row.type.name,
                    row.before,
                    row.after,
                    row.safetyLevel.name,
                    row.status,
                    row.warning,
                    row.path,
                )
            )
        }
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(applyAction, backAction, cancelAction)

    override fun createCenterPanel(): JComponent {
        val countsByType = if (plan.summary.selectedCountByGroup.isEmpty()) {
            "No selected items."
        } else {
            plan.summary.selectedCountByGroup.entries.joinToString("<br>") { (group, count) ->
                "${group.displayName}: $count"
            }
        }
        val summary = JBLabel(
            "<html><b>Will rename:</b> ${plan.summary.selectedCount}<br>" +
                "<b>Skipped:</b> ${plan.summary.skippedCount}<br>" +
                "<b>Blocked:</b> ${plan.summary.blockedCount}<br>" +
                "<b>Selected by type:</b><br>$countsByType<br>" +
                "<b>Preview log:</b> $previewSessionPath</html>"
        )

        return JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(1320, 720)
            add(summary, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
        }
    }

    fun showAndGetChoice(): Choice {
        show()
        return choice
    }

    private inner class ApplyAction : DialogWrapperAction("Apply") {
        override fun doAction(e: java.awt.event.ActionEvent?) {
            choice = Choice.APPLY
            close(OK_EXIT_CODE)
        }
    }

    private inner class BackAction : DialogWrapperAction("Back") {
        override fun doAction(e: java.awt.event.ActionEvent?) {
            choice = Choice.BACK
            close(NEXT_USER_EXIT_CODE)
        }
    }
}
