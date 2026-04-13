package com.internal.refactorassistant.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.internal.refactorassistant.model.CustomNamingRule
import com.internal.refactorassistant.model.RefactorOptions
import com.internal.refactorassistant.model.RefactorRequest

@State(
    name = "InternalProjectRefactorAssistantSettings",
    storages = [Storage("internalProjectRefactorAssistant.xml")],
)
@Service(Service.Level.APP)
class InternalRefactorAssistantSettingsService : PersistentStateComponent<InternalRefactorAssistantSettingsState> {
    private var state = InternalRefactorAssistantSettingsState()

    override fun getState(): InternalRefactorAssistantSettingsState = state

    override fun loadState(state: InternalRefactorAssistantSettingsState) {
        this.state = state
    }

    fun defaults(): InternalRefactorAssistantSettingsState = state.copy(
        recentRequests = state.recentRequests.toList(),
        customNamingRules = state.customNamingRules.toList(),
    )

    fun rememberRequest(request: RefactorRequest) {
        val compact = RequestHistoryEntry.from(request)
        val updated = listOf(compact) + state.recentRequests.filterNot { it.identityKey == compact.identityKey }
        state = state.copy(recentRequests = updated.take(10))
    }

    fun updateDefaultOptions(options: RefactorOptions) {
        state = state.copy(defaultOptions = options)
    }

    fun updateCommitMessageTemplate(template: String) {
        state = state.copy(commitMessageTemplate = template)
    }

    fun updateGradleTasks(tasks: List<String>) {
        state = state.copy(defaultGradleTasks = tasks)
    }

    fun updateCustomRules(rules: List<CustomNamingRule>) {
        state = state.copy(customNamingRules = rules)
    }

    companion object {
        fun getInstance(): InternalRefactorAssistantSettingsService =
            ApplicationManager.getApplication().getService(InternalRefactorAssistantSettingsService::class.java)
    }
}

data class InternalRefactorAssistantSettingsState @JvmOverloads constructor(
    var defaultOptions: RefactorOptions = RefactorOptions(),
    var commitMessageTemplate: String = "refactor(template): duplicate <oldFeature> to <newFeature>",
    var defaultGradleTasks: List<String> = listOf("assembleDebug"),
    var customNamingRules: List<CustomNamingRule> = emptyList(),
    var recentRequests: List<RequestHistoryEntry> = emptyList(),
)

data class RequestHistoryEntry @JvmOverloads constructor(
    var oldFeatureName: String = "",
    var newFeatureName: String = "",
    var oldDisplayName: String = "",
    var newDisplayName: String = "",
    var oldPackagePrefix: String = "",
    var newPackagePrefix: String = "",
    var selectedModules: List<String> = emptyList(),
    var synonymDictionaryText: String = "",
    var options: RefactorOptions = RefactorOptions(),
) {
    val identityKey: String
        get() = listOf(oldFeatureName, newFeatureName, oldPackagePrefix, newPackagePrefix).joinToString("|")

    companion object {
        fun from(request: RefactorRequest): RequestHistoryEntry = RequestHistoryEntry(
            oldFeatureName = request.oldFeatureName,
            newFeatureName = request.newFeatureName,
            oldDisplayName = request.oldDisplayName,
            newDisplayName = request.newDisplayName,
            oldPackagePrefix = request.oldPackagePrefix,
            newPackagePrefix = request.newPackagePrefix,
            selectedModules = request.selectedModules,
            synonymDictionaryText = request.synonymDictionaryText,
            options = request.options,
        )
    }
}
