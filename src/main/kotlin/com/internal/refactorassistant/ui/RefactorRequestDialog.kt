package com.internal.refactorassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.internal.refactorassistant.model.RefactorOptions
import com.internal.refactorassistant.model.RefactorRequest
import com.internal.refactorassistant.settings.InternalRefactorAssistantSettingsState
import com.internal.refactorassistant.settings.RequestHistoryEntry
import com.internal.refactorassistant.util.ValidationUtil
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class RefactorRequestDialog(
    project: Project,
    private val settingsState: InternalRefactorAssistantSettingsState,
    moduleNames: List<String>,
) : DialogWrapper(project) {
    private val recent = settingsState.recentRequests.firstOrNull() ?: RequestHistoryEntry()
    private val defaults = if (settingsState.recentRequests.isEmpty()) settingsState.defaultOptions else recent.options

    private val oldFeatureField = JBTextField(recent.oldFeatureName)
    private val newFeatureField = JBTextField(recent.newFeatureName)
    private val oldDisplayField = JBTextField(recent.oldDisplayName)
    private val newDisplayField = JBTextField(recent.newDisplayName)
    private val oldPackageField = JBTextField(recent.oldPackagePrefix)
    private val newPackageField = JBTextField(recent.newPackagePrefix)
    private val synonymsArea = JBTextArea(recent.synonymDictionaryText, 8, 60)

    private val modulesModel = CollectionListModel(moduleNames)
    private val modulesList = JBList(modulesModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        visibleRowCount = minOf(moduleNames.size.coerceAtLeast(3), 8)
        setSelectedIndices(moduleNames.indices.toList().toIntArray())
    }

    private val renamePackagesCheck = JBCheckBox("Rename packages", defaults.renamePackages)
    private val renameClassesFilesCheck = JBCheckBox("Rename classes/files", defaults.renameClassesAndFiles)
    private val renameVariablesFunctionsCheck = JBCheckBox("Rename variables/functions (safe mode only)", defaults.renameVariablesAndFunctions)
    private val renameLayoutsCheck = JBCheckBox("Rename layouts", defaults.renameLayouts)
    private val renameDrawablesCheck = JBCheckBox("Rename drawables", defaults.renameDrawables)
    private val renameFontsCheck = JBCheckBox("Rename fonts", defaults.renameFonts)
    private val renameAnimCheck = JBCheckBox("Rename anim", defaults.renameAnim)
    private val renameRawCheck = JBCheckBox("Rename raw", defaults.renameRaw)
    private val renameStringsCheck = JBCheckBox("Rename strings", defaults.renameStrings)
    private val renameDimensCheck = JBCheckBox("Rename dimens", defaults.renameDimens)
    private val renameStylesCheck = JBCheckBox("Rename styles/themes", defaults.renameStylesThemes)
    private val reformatCheck = JBCheckBox("Reformat Kotlin/XML", defaults.reformatCode)
    private val optimizeImportsCheck = JBCheckBox("Optimize imports", defaults.optimizeImports)
    private val runGradleBuildCheck = JBCheckBox("Run Gradle build after refactor", defaults.runGradleBuildAfterRefactor)
    private val createGitCommitCheck = JBCheckBox("Create git commit after refactor", defaults.createGitCommitAfterRefactor)
    private val dryRunCheck = JBCheckBox("Dry-run only", defaults.dryRunOnly)

    init {
        title = "Duplicate Template Project"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val modulesPane = JBScrollPane(modulesList).apply {
            preferredSize = Dimension(240, 120)
        }
        val synonymsPane = JBScrollPane(synonymsArea).apply {
            preferredSize = Dimension(520, 140)
        }
        return panel {
            row("Old feature name") { cell(oldFeatureField).align(Align.FILL) }
            row("New feature name") { cell(newFeatureField).align(Align.FILL) }
            row("Old display word") { cell(oldDisplayField).align(Align.FILL) }
            row("New display word") { cell(newDisplayField).align(Align.FILL) }
            row("Old package prefix") { cell(oldPackageField).align(Align.FILL) }
            row("New package prefix") { cell(newPackageField).align(Align.FILL) }
            row("Modules to include") { cell(modulesPane).align(Align.FILL) }
            row("Synonym JSON/YAML") { cell(synonymsPane).align(Align.FILL) }
            group("Rename Scope") {
                row {
                    cell(renamePackagesCheck)
                    cell(renameClassesFilesCheck)
                    cell(renameVariablesFunctionsCheck)
                }
                row {
                    cell(renameLayoutsCheck)
                    cell(renameDrawablesCheck)
                    cell(renameFontsCheck)
                    cell(renameAnimCheck)
                    cell(renameRawCheck)
                }
                row {
                    cell(renameStringsCheck)
                    cell(renameDimensCheck)
                    cell(renameStylesCheck)
                }
            }
            group("Post Steps") {
                row {
                    cell(reformatCheck)
                    cell(optimizeImportsCheck)
                    cell(runGradleBuildCheck)
                }
                row {
                    cell(createGitCommitCheck)
                    cell(dryRunCheck)
                }
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (oldFeatureField.text.isBlank()) return ValidationInfo("Old feature name is required", oldFeatureField)
        if (newFeatureField.text.isBlank()) return ValidationInfo("New feature name is required", newFeatureField)
        if (oldDisplayField.text.isBlank()) return ValidationInfo("Old display name is required", oldDisplayField)
        if (newDisplayField.text.isBlank()) return ValidationInfo("New display name is required", newDisplayField)
        if (!ValidationUtil.isValidPackageName(oldPackageField.text.trim())) {
            return ValidationInfo("Old package prefix must be lowercase dot-separated", oldPackageField)
        }
        if (!ValidationUtil.isValidPackageName(newPackageField.text.trim())) {
            return ValidationInfo("New package prefix must be lowercase dot-separated", newPackageField)
        }
        return null
    }

    fun toRequest(customRules: List<com.internal.refactorassistant.model.CustomNamingRule>): RefactorRequest = RefactorRequest(
        oldFeatureName = oldFeatureField.text.trim(),
        newFeatureName = newFeatureField.text.trim(),
        oldDisplayName = oldDisplayField.text.trim(),
        newDisplayName = newDisplayField.text.trim(),
        oldPackagePrefix = oldPackageField.text.trim(),
        newPackagePrefix = newPackageField.text.trim(),
        synonymDictionaryText = synonymsArea.text.trim(),
        selectedModules = modulesList.selectedValuesList,
        options = RefactorOptions(
            renamePackages = renamePackagesCheck.isSelected,
            renameClassesAndFiles = renameClassesFilesCheck.isSelected,
            renameVariablesAndFunctions = renameVariablesFunctionsCheck.isSelected,
            renameLayouts = renameLayoutsCheck.isSelected,
            renameDrawables = renameDrawablesCheck.isSelected,
            renameFonts = renameFontsCheck.isSelected,
            renameAnim = renameAnimCheck.isSelected,
            renameRaw = renameRawCheck.isSelected,
            renameStrings = renameStringsCheck.isSelected,
            renameDimens = renameDimensCheck.isSelected,
            renameStylesThemes = renameStylesCheck.isSelected,
            reformatCode = reformatCheck.isSelected,
            optimizeImports = optimizeImportsCheck.isSelected,
            runGradleBuildAfterRefactor = runGradleBuildCheck.isSelected,
            createGitCommitAfterRefactor = createGitCommitCheck.isSelected,
            dryRunOnly = dryRunCheck.isSelected,
        ),
        customNamingRules = customRules,
    )
}
