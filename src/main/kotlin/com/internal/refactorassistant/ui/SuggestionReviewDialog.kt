package com.internal.refactorassistant.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.Alarm
import com.internal.refactorassistant.model.ProjectScanResult
import com.internal.refactorassistant.model.RefactorSelectionGroup
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ReviewScreenState
import com.internal.refactorassistant.model.UsedNamesRegistry
import com.internal.refactorassistant.preview.ReviewValidationService
import com.internal.refactorassistant.selection.ReviewSelectionCoordinator
import com.internal.refactorassistant.selection.TypeGrouping
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class SuggestionReviewDialog(
    project: Project,
    private val scanResult: ProjectScanResult,
    private val coordinator: ReviewSelectionCoordinator,
    initialState: ReviewScreenState,
    private val registry: UsedNamesRegistry,
    private val topWarnings: List<String>,
) : DialogWrapper(project, true) {
    enum class Choice {
        PREVIEW,
        BACK,
        CANCEL,
    }

    private val validator = ReviewValidationService(scanResult.existingNamesByType, registry)
    private val reloadAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private var currentState: ReviewScreenState = initialState
    private var choice: Choice = Choice.CANCEL
    private var suppressUiEvents = false

    private val groupCheckboxes = TypeGrouping.orderedGroups.associateWith { JBCheckBox() }
    private val selectAllTypesCheckbox = JBCheckBox("Select All Types")
    private val selectAllItemsCheckbox = JBCheckBox("Select All Items")
    private val showUsedNamesCheckbox = JBCheckBox("Show previously used names")
    private val summaryLabel = JBLabel()
    private val reloadLabel = JBLabel("Suggestions are up to date.")
    private val topInfoLabel = JBLabel()
    private val tableModel = SuggestionTableModel(
        initialRows = initialState.reviewItems,
        validator = validator,
        onSelectionChanged = { itemId, selected ->
            scheduleReload { coordinator.setItemSelected(itemId, selected) }
        },
        onManualNameChanged = { itemId, value ->
            currentState = coordinator.setManualName(itemId, value)
            applyState(currentState)
        },
    )
    private val table = JBTable(tableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_OFF
        preferredScrollableViewportSize = Dimension(1120, 560)
    }
    private val previewAction = PreviewAction()
    private val backAction = BackAction()

    init {
        title = "Reskin Refactor - Type Selection + Suggestion Review"
        previewAction.isEnabled = currentState.reviewItems.isNotEmpty()
        init()
        installListeners()
        applyState(currentState)
    }

    override fun createActions(): Array<Action> = arrayOf(previewAction, backAction, cancelAction)

    override fun createCenterPanel(): JComponent {
        val typePanel = JPanel(GridLayout(0, 1, 0, 4))
        typePanel.add(selectAllTypesCheckbox)
        typePanel.add(selectAllItemsCheckbox)
        typePanel.add(showUsedNamesCheckbox)
        TypeGrouping.orderedGroups.forEach { group ->
            typePanel.add(groupCheckboxes.getValue(group))
        }

        val leftPanel = JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(280, 720)
            add(JBLabel("<html><b>Type selector</b><br>Batch selection reloads automatically.</html>"), BorderLayout.NORTH)
            add(JBScrollPane(typePanel), BorderLayout.CENTER)
            add(reloadLabel, BorderLayout.SOUTH)
        }

        val rightTop = JPanel(BorderLayout(0, 8)).apply {
            add(topInfoLabel, BorderLayout.NORTH)
            add(summaryLabel, BorderLayout.SOUTH)
        }

        val rightPanel = JPanel(BorderLayout(0, 8)).apply {
            add(rightTop, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
        }

        val root = JPanel(BorderLayout(12, 0)).apply {
            preferredSize = Dimension(1480, 760)
        }
        if (topWarnings.isNotEmpty()) {
            root.add(
                JBLabel("<html><b>History warnings:</b><br>${topWarnings.joinToString("<br>")}</html>"),
                BorderLayout.NORTH,
            )
        }
        root.add(leftPanel, BorderLayout.WEST)
        root.add(rightPanel, BorderLayout.CENTER)
        return root
    }

    fun showAndGetChoice(): Choice {
        show()
        return choice
    }

    fun reviewItems(): List<ReviewItemState> = currentState.reviewItems

    fun selectedGroups(): Set<RefactorSelectionGroup> = currentState.selectedGroups

    private fun installListeners() {
        selectAllTypesCheckbox.addActionListener {
            if (suppressUiEvents) return@addActionListener
            scheduleReload { coordinator.setAllTypesSelected(selectAllTypesCheckbox.isSelected) }
        }
        selectAllItemsCheckbox.addActionListener {
            if (suppressUiEvents) return@addActionListener
            scheduleReload { coordinator.setAllActiveItemsSelected(selectAllItemsCheckbox.isSelected) }
        }
        showUsedNamesCheckbox.addActionListener {
            if (suppressUiEvents) return@addActionListener
            scheduleReload { coordinator.setShowPreviouslyUsedNames(showUsedNamesCheckbox.isSelected) }
        }
        groupCheckboxes.forEach { (group, checkbox) ->
            checkbox.addActionListener {
                if (suppressUiEvents) return@addActionListener
                scheduleReload { coordinator.setGroupSelected(group, checkbox.isSelected) }
            }
        }
    }

    private fun scheduleReload(reload: () -> ReviewScreenState) {
        reloadLabel.text = "Reloading suggestions..."
        reloadAlarm.cancelAllRequests()
        reloadAlarm.addRequest(
            {
                val newState = reload()
                ApplicationManager.getApplication().invokeLater(
                    {
                        currentState = newState
                        applyState(newState)
                        reloadLabel.text = "Suggestions are up to date."
                    },
                    ModalityState.any(),
                )
            },
            400,
        )
    }

    private fun applyState(state: ReviewScreenState) {
        currentState = state
        suppressUiEvents = true
        try {
            tableModel.replaceRows(state.reviewItems)
            topInfoLabel.text = buildString {
                append("<html><b>Root package:</b> ${scanResult.rootPackage ?: "not detected"}<br>")
                append("<b>Scanned items:</b> ${scanResult.items.size}<br>")
                append("<b>Active items:</b> ${state.reviewItems.size}</html>")
            }
            summaryLabel.text = buildString {
                val blocked = state.reviewItems.count { validator.validate(it, state.reviewItems).blocked }
                append("Selected items: ${state.reviewItems.count { it.applySelected }}")
                append(" | Blocked: $blocked")
                append(" | Active groups: ${state.selectedGroups.size}")
            }
            selectAllTypesCheckbox.isSelected = state.selectAllTypes
            selectAllItemsCheckbox.isSelected = state.selectAllItems
            showUsedNamesCheckbox.isSelected = state.showPreviouslyUsedNames
            state.groupInfos.forEach { info ->
                val checkbox = groupCheckboxes.getValue(info.group)
                checkbox.text = "${info.group.displayName} (${info.selectedItemCount}/${info.totalCount})"
                checkbox.isSelected = info.active
                checkbox.isEnabled = info.totalCount > 0
            }
            previewAction.isEnabled = state.reviewItems.isNotEmpty()
        } finally {
            suppressUiEvents = false
        }
    }

    private inner class PreviewAction : DialogWrapperAction("Preview") {
        override fun doAction(e: java.awt.event.ActionEvent?) {
            choice = Choice.PREVIEW
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
