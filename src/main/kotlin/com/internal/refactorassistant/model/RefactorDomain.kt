package com.internal.refactorassistant.model

data class ScanSettings(
    val selectedModules: List<String>,
    val scanKotlinClassesFiles: Boolean,
    val scanActivities: Boolean,
    val scanFragments: Boolean,
    val scanViewModels: Boolean,
    val scanAdapters: Boolean,
    val scanFeaturePackages: Boolean,
    val scanLayouts: Boolean,
    val scanDrawables: Boolean,
    val scanStrings: Boolean,
    val scanDimens: Boolean,
    val versionLabel: String,
    val note: String,
)

data class WorkflowSessionInput(
    val settings: ScanSettings,
    val showPreviouslyUsedNames: Boolean = false,
)

enum class RefactorItemType {
    PACKAGE_CHILD,
    KOTLIN_FILE,
    KOTLIN_CLASS,
    ACTIVITY,
    FRAGMENT,
    VIEWMODEL,
    ADAPTER,
    LAYOUT,
    DRAWABLE,
    STRING,
    DIMEN,
}

enum class RefactorSelectionGroup(
    val displayName: String,
    val selectableByDefault: Boolean = true,
) {
    ACTIVITY("Activity"),
    FRAGMENT("Fragment"),
    VIEWMODEL("ViewModel"),
    ADAPTER("Adapter"),
    KOTLIN_CLASS_OR_FILE("KotlinClassOrFile"),
    FEATURE_PACKAGE("FeaturePackage"),
    LAYOUT_FILE("LayoutFile"),
    DRAWABLE_FILE("DrawableFile"),
    STRING_KEY("StringKey"),
    DIMEN_KEY("DimenKey"),
    OTHER_RESOURCE("OtherResource"),
    REVIEW_REQUIRED("ReviewRequired"),
    DO_NOT_TOUCH("DoNotTouch", selectableByDefault = false),
}

enum class SafetyLevel {
    SAFE_AUTO,
    REVIEW_REQUIRED,
    DO_NOT_TOUCH,
}

enum class SessionMode {
    PREVIEW,
    APPLY,
}

enum class SessionItemResult {
    PREVIEWED,
    APPLIED,
    SKIPPED,
    FAILED,
    BLOCKED,
}

data class UsedNameMetadata(
    val usedBefore: Boolean,
    val lastUsedVersion: String?,
    val lastUsedTimestamp: String?,
)

enum class SuggestionSource(
    val rank: Int,
) {
    GEMINI_SEMANTIC(1),
    EXACT_PHRASE(2),
    TOKEN_SYNONYM(3),
    TOKEN_ABBREVIATION(4),
    WHOLE_PHRASE_REPLACEMENT(5),
    RULE_FALLBACK(6),
    MANUAL(7),
}

data class SuggestionCandidate(
    val value: String,
    val rawValue: String,
    val normalizationNote: String?,
    val usedMetadata: UsedNameMetadata,
    val source: SuggestionSource,
)

data class ScannedRefactorItem(
    val id: String,
    val type: RefactorItemType,
    val oldName: String,
    val displayPath: String,
    val absolutePath: String,
    val moduleName: String,
    val safetyLevel: SafetyLevel,
    val details: ItemDetails = ItemDetails(),
)

data class ProjectScanResult(
    val moduleNames: List<String>,
    val rootPackage: String?,
    val items: List<ScannedRefactorItem>,
    val existingNamesByType: Map<RefactorItemType, Set<String>>,
    val referenceFiles: List<String>,
)

data class ItemDetails(
    val packageName: String? = null,
    val rootPackage: String? = null,
    val packageRelativePath: String? = null,
    val sourceRootPath: String? = null,
    val xmlFilePath: String? = null,
    val xmlTagName: String? = null,
    val resFolderName: String? = null,
)

data class ReviewItemState(
    val item: ScannedRefactorItem,
    val suggestions: List<SuggestionCandidate>,
    val groupKey: String,
    val canonicalNewName: String,
    val groupSize: Int,
    var overrideApplied: Boolean,
    var selectedNewName: String,
    var selectedSuggestionSource: SuggestionSource?,
    var applySelected: Boolean,
    var status: String,
    var warning: String,
    val providerUsed: String = "rule-based",
    val semanticConfidence: Double? = null,
    val semanticExplanation: String = "",
    val rawAiCandidates: List<String> = emptyList(),
)

data class ReviewValidation(
    val blocked: Boolean,
    val warnings: List<String>,
)

data class PreviewRow(
    val type: RefactorItemType,
    val safetyLevel: SafetyLevel,
    val before: String,
    val after: String,
    val rawCandidate: String,
    val normalizationNote: String,
    val groupKey: String,
    val canonicalNewName: String,
    val groupSize: Int,
    val overrideStatus: String,
    val suggestionSource: String,
    val candidateRank: String,
    val moduleName: String,
    val path: String,
    val status: String,
    val warning: String,
    val providerUsed: String,
    val semanticConfidence: String,
    val semanticExplanation: String,
    val rawAiCandidates: String,
)

data class PreviewSummary(
    val selectedCount: Int,
    val skippedCount: Int,
    val blockedCount: Int,
    val selectedCountByGroup: Map<RefactorSelectionGroup, Int>,
)

data class PreviewPlan(
    val rows: List<PreviewRow>,
    val warnings: List<String>,
    val summary: PreviewSummary,
)

data class GroupSelectionInfo(
    val group: RefactorSelectionGroup,
    val totalCount: Int,
    val selectedItemCount: Int,
    val active: Boolean,
)

data class ReviewScreenState(
    val reviewItems: List<ReviewItemState>,
    val groupInfos: List<GroupSelectionInfo>,
    val selectedGroups: Set<RefactorSelectionGroup>,
    val selectAllTypes: Boolean,
    val selectAllItems: Boolean,
    val showPreviouslyUsedNames: Boolean,
)

data class HistoryEntry(
    val type: RefactorItemType,
    val oldName: String,
    val newName: String,
    val timestamp: String,
    val versionLabel: String,
    val status: String,
)

data class HistoryFile(
    val entries: List<HistoryEntry> = emptyList(),
)

data class UsedNameEntry(
    val name: String,
    val lastUsedVersion: String,
    val lastUsedTimestamp: String,
)

data class UsedNamesRegistry(
    val namesByType: Map<RefactorItemType, List<UsedNameEntry>> = emptyMap(),
)

data class SessionSummary(
    val totalItems: Int,
    val selectedItems: Int,
    val skippedItems: Int,
    val blockedItems: Int,
    val appliedItems: Int,
    val failedItems: Int,
)

data class SessionItemRecord(
    val type: RefactorItemType,
    val oldName: String,
    val suggestedNames: List<String>,
    val selectedNewName: String?,
    val result: SessionItemResult,
    val warning: String?,
)

data class SessionRecord(
    val sessionId: String,
    val timestamp: String,
    val mode: SessionMode,
    val versionLabel: String,
    val userNote: String,
    val selectedTypes: List<RefactorSelectionGroup>,
    val selectedItemCount: Int,
    val summary: SessionSummary,
    val items: List<SessionItemRecord>,
)

data class ProjectRefactorFiles(
    val rootDirectory: String,
    val historyFile: String,
    val usedNamesFile: String,
    val sessionsDirectory: String,
)

data class LoadStateResult(
    val history: HistoryFile,
    val registry: UsedNamesRegistry,
    val warnings: List<String>,
)

data class ApplyResult(
    val appliedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val blockedCount: Int,
    val warnings: List<String>,
    val errors: List<String>,
    val changedFiles: List<String>,
    val sessionLogPath: String,
    val rows: List<ApplyResultRow>,
)

data class ApplyExecutionOutcome(
    val itemResults: Map<String, SessionItemResult>,
    val warningsByItemId: Map<String, String>,
    val historyEntries: List<HistoryEntry>,
    val warnings: List<String>,
    val errors: List<String>,
    val changedFiles: List<String>,
)

data class ApplyResultRow(
    val type: RefactorItemType,
    val before: String,
    val after: String,
    val finalStatus: String,
    val reason: String,
    val path: String,
)

data class ApplyProgressUpdate(
    val totalItems: Int,
    val processedItems: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val currentItemLabel: String,
)
