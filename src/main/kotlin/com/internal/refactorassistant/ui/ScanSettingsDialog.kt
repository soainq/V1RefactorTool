package com.internal.refactorassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.internal.refactorassistant.model.ScanSettings
import com.internal.refactorassistant.settings.GeminiProviderMode
import com.internal.refactorassistant.settings.GeminiSettingsService
import com.internal.refactorassistant.settings.WorkflowDefaults
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class ScanSettingsDialog(
    project: Project,
    moduleNames: List<String>,
    private val loadWarnings: List<String>,
    initialSettings: ScanSettings? = null,
) : DialogWrapper(project, true) {
    private val geminiSettingsService = GeminiSettingsService.getInstance()
    private val moduleCheckboxes = moduleNames.map { moduleName -> JBCheckBox(moduleName, true) }
    private val scanKotlinFilesCheckbox = JBCheckBox("Scan Kotlin class/file", initialSettings?.scanKotlinClassesFiles ?: true)
    private val scanActivityCheckbox = JBCheckBox("Scan Activity", initialSettings?.scanActivities ?: true)
    private val scanFragmentCheckbox = JBCheckBox("Scan Fragment", initialSettings?.scanFragments ?: true)
    private val scanViewModelCheckbox = JBCheckBox("Scan ViewModel", initialSettings?.scanViewModels ?: true)
    private val scanAdapterCheckbox = JBCheckBox("Scan Adapter", initialSettings?.scanAdapters ?: true)
    private val scanFeaturePackageCheckbox = JBCheckBox("Scan FeaturePackage", initialSettings?.scanFeaturePackages ?: false)
    private val scanLayoutCheckbox = JBCheckBox("Scan layout", initialSettings?.scanLayouts ?: true)
    private val scanDrawableCheckbox = JBCheckBox("Scan drawable", initialSettings?.scanDrawables ?: true)
    private val scanStringCheckbox = JBCheckBox("Scan string", initialSettings?.scanStrings ?: true)
    private val scanDimenCheckbox = JBCheckBox("Scan dimen", initialSettings?.scanDimens ?: true)
    private val versionField = JBTextField(initialSettings?.versionLabel ?: WorkflowDefaults.DEFAULT_VERSION_LABEL)
    private val noteField = JBTextArea(3, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        text = initialSettings?.note.orEmpty()
    }
    private val aiEnabledCheckbox = JBCheckBox("Enable AI semantic provider")
    private val providerModeComboBox = JComboBox(GeminiProviderMode.entries.toTypedArray())
    private val aiStatusLabel = JBLabel()

    init {
        if (initialSettings != null) {
            moduleCheckboxes.forEach { checkbox ->
                checkbox.isSelected = checkbox.text in initialSettings.selectedModules
            }
        }
        val geminiSettings = geminiSettingsService.currentResolved()
        aiEnabledCheckbox.isSelected = geminiSettings.enableGeminiSemanticProvider
        providerModeComboBox.selectedItem = geminiSettings.providerMode
        updateAiStatusLabel()
    }

    init {
        title = "Reskin Refactor - Scan Settings"
        setOKButtonText("Scan")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val modulesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            moduleCheckboxes.forEach { add(it) }
        }

        val scanOptionsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(scanKotlinFilesCheckbox)
            add(scanActivityCheckbox)
            add(scanFragmentCheckbox)
            add(scanViewModelCheckbox)
            add(scanAdapterCheckbox)
            add(scanFeaturePackageCheckbox)
            add(scanLayoutCheckbox)
            add(scanDrawableCheckbox)
            add(scanStringCheckbox)
            add(scanDimenCheckbox)
        }

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Modules", JBScrollPane(modulesPanel))
            .addLabeledComponent("Scan options", scanOptionsPanel)
            .addComponent(JBLabel("<html><b>AI provider</b></html>"))
            .addComponent(aiEnabledCheckbox)
            .addLabeledComponent("Provider mode", providerModeComboBox)
            .addComponent(aiStatusLabel)
            .addLabeledComponent("Version label", versionField)
            .addLabeledComponent("Note", JBScrollPane(noteField))
            .panel

        val root = JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(700, 520)
        }

        if (loadWarnings.isNotEmpty()) {
            root.add(
                JBLabel(
                    "<html><b>History warnings:</b><br>${loadWarnings.joinToString("<br>")}</html>"
                ),
                BorderLayout.NORTH,
            )
        }
        root.add(form, BorderLayout.CENTER)
        aiEnabledCheckbox.addActionListener { updateAiStatusLabel() }
        providerModeComboBox.addActionListener { updateAiStatusLabel() }
        return root
    }

    override fun doValidate(): ValidationInfo? {
        if (moduleCheckboxes.none { it.isSelected }) {
            return ValidationInfo("Select at least one module to scan.")
        }
        if (
            listOf(
                scanKotlinFilesCheckbox,
                scanActivityCheckbox,
                scanFragmentCheckbox,
                scanViewModelCheckbox,
                scanAdapterCheckbox,
                scanFeaturePackageCheckbox,
                scanLayoutCheckbox,
                scanDrawableCheckbox,
                scanStringCheckbox,
                scanDimenCheckbox,
            ).none { it.isSelected }
        ) {
            return ValidationInfo("Enable at least one scan target.")
        }
        return null
    }

    fun toScanSettings(): ScanSettings = ScanSettings(
        selectedModules = moduleCheckboxes.filter { it.isSelected }.map { it.text },
        scanKotlinClassesFiles = scanKotlinFilesCheckbox.isSelected,
        scanActivities = scanActivityCheckbox.isSelected,
        scanFragments = scanFragmentCheckbox.isSelected,
        scanViewModels = scanViewModelCheckbox.isSelected,
        scanAdapters = scanAdapterCheckbox.isSelected,
        scanFeaturePackages = scanFeaturePackageCheckbox.isSelected,
        scanLayouts = scanLayoutCheckbox.isSelected,
        scanDrawables = scanDrawableCheckbox.isSelected,
        scanStrings = scanStringCheckbox.isSelected,
        scanDimens = scanDimenCheckbox.isSelected,
        versionLabel = versionField.text.trim().ifBlank { WorkflowDefaults.DEFAULT_VERSION_LABEL },
        note = noteField.text.trim(),
    )

    fun applyAiSettings() {
        geminiSettingsService.update {
            enableGeminiSemanticProvider = aiEnabledCheckbox.isSelected
            providerMode = providerModeComboBox.selectedItem as? GeminiProviderMode ?: GeminiProviderMode.RULE_BASED_ONLY
        }
    }

    private fun updateAiStatusLabel() {
        val effectiveKey = geminiSettingsService.maskedEffectiveApiKey()
        val mode = providerModeComboBox.selectedItem as? GeminiProviderMode ?: GeminiProviderMode.RULE_BASED_ONLY
        aiStatusLabel.text = when {
            !aiEnabledCheckbox.isSelected || mode == GeminiProviderMode.RULE_BASED_ONLY ->
                "AI status: rule-based only"
            effectiveKey.isBlank() ->
                "AI status: API key required. A prompt will appear before scan."
            else ->
                "AI status: key configured ($effectiveKey)"
        }
    }
}
