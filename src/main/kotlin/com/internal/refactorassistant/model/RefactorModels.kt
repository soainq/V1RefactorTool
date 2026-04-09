package com.internal.refactorassistant.model

import java.time.Instant

data class RefactorRequest @JvmOverloads constructor(
    val oldFeatureName: String,
    val newFeatureName: String,
    val oldDisplayName: String,
    val newDisplayName: String,
    val oldPackagePrefix: String,
    val newPackagePrefix: String,
    val synonymDictionaryText: String = "",
    val selectedModules: List<String> = emptyList(),
    val options: RefactorOptions = RefactorOptions(),
    val customNamingRules: List<CustomNamingRule> = emptyList(),
)

data class RefactorOptions @JvmOverloads constructor(
    var renamePackages: Boolean = true,
    var renameClassesAndFiles: Boolean = true,
    var renameVariablesAndFunctions: Boolean = false,
    var renameLayouts: Boolean = true,
    var renameDrawables: Boolean = true,
    var renameFonts: Boolean = false,
    var renameAnim: Boolean = false,
    var renameRaw: Boolean = false,
    var renameStrings: Boolean = true,
    var renameDimens: Boolean = true,
    var renameStylesThemes: Boolean = false,
    var reformatCode: Boolean = true,
    var optimizeImports: Boolean = false,
    var runGradleBuildAfterRefactor: Boolean = false,
    var createGitCommitAfterRefactor: Boolean = false,
    var dryRunOnly: Boolean = true,
)

data class CustomNamingRule @JvmOverloads constructor(
    var description: String = "",
    var pattern: String = "",
    var replacement: String = "",
    var targets: Set<RenameTargetKind> = emptySet(),
    var enabled: Boolean = true,
)

enum class RenameTargetKind {
    PACKAGE,
    KOTLIN_CLASS,
    KOTLIN_FILE,
    VARIABLE,
    FUNCTION,
    LAYOUT,
    DRAWABLE,
    STRING,
    DIMEN,
    FONT,
    ANIM,
    RAW,
    STYLE_THEME,
    PACKAGE_REFERENCE,
    RESOURCE_REFERENCE,
}

data class SynonymDictionary @JvmOverloads constructor(
    val tokens: Map<String, String> = emptyMap(),
)

data class ScanResult(
    val modules: List<ModuleSnapshot>,
    val sourceFiles: List<SourceFileSnapshot>,
    val resourceFiles: List<ResourceFileSnapshot>,
    val valueResources: List<ValueResourceSnapshot>,
    val referenceFiles: List<ReferenceFileSnapshot>,
)

data class ModuleSnapshot(
    val name: String,
    val contentRoots: List<String>,
    val sourceRoots: List<String>,
)

enum class SourceLanguage {
    KOTLIN,
    JAVA,
}

data class SourceFileSnapshot(
    val moduleName: String,
    val path: String,
    val sourceRootPath: String,
    val packageName: String,
    val fileName: String,
    val classNames: List<String>,
    val language: SourceLanguage,
)

data class ResourceFileSnapshot(
    val moduleName: String,
    val path: String,
    val directoryName: String,
    val resourceName: String,
    val extension: String,
    val kind: ResourceRenameKind,
)

data class ValueResourceSnapshot(
    val moduleName: String,
    val xmlFilePath: String,
    val tagName: String,
    val resourceName: String,
    val kind: ResourceRenameKind,
)

data class ReferenceFileSnapshot(
    val moduleName: String,
    val path: String,
    val fileType: String,
)

data class RefactorPlan(
    val request: RefactorRequest,
    val generatedAt: Instant = Instant.now(),
    val packageOperations: List<PackageRenameOperation>,
    val sourceOperations: List<KotlinRenameOperation>,
    val resourceOperations: List<ResourceRenameOperation>,
    val referenceOperations: List<ReferenceRewriteOperation>,
    val conflicts: List<PlanIssue>,
    val warnings: List<PlanIssue>,
    val counts: PlanCounts,
)

data class PlanCounts(
    val packageOperations: Int,
    val sourceOperations: Int,
    val resourceOperations: Int,
    val referenceOperations: Int,
    val conflicts: Int,
    val warnings: Int,
    val affectedFiles: Int,
    val affectedReferences: Int,
)

enum class IssueSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class PlanIssue(
    val severity: IssueSeverity,
    val code: String,
    val message: String,
    val path: String? = null,
    val before: String? = null,
    val after: String? = null,
)

interface PlannedOperation {
    val id: String
    val category: String
    val itemType: String
    val before: String
    val after: String
    val path: String?
    val moduleName: String
    val affectedReferenceCount: Int
}

data class PackageRenameOperation(
    override val id: String,
    override val moduleName: String,
    val sourceRootPath: String,
    val oldPackage: String,
    val newPackage: String,
    val affectedFilePaths: List<String>,
    override val affectedReferenceCount: Int,
) : PlannedOperation {
    override val category: String = "package"
    override val itemType: String = "package"
    override val before: String = oldPackage
    override val after: String = newPackage
    override val path: String = sourceRootPath
}

enum class KotlinRenameKind {
    KOTLIN_CLASS,
    KOTLIN_FILE,
}

data class KotlinRenameOperation(
    override val id: String,
    override val moduleName: String,
    val filePath: String,
    val packageName: String,
    val declarationName: String?,
    val kind: KotlinRenameKind,
    val oldName: String,
    val newName: String,
    override val affectedReferenceCount: Int,
) : PlannedOperation {
    override val category: String = "source"
    override val itemType: String = kind.name.lowercase().replace('_', ' ')
    override val before: String = oldName
    override val after: String = newName
    override val path: String = filePath
}

enum class ResourceRenameKind {
    LAYOUT_FILE,
    DRAWABLE_FILE,
    STRING_VALUE,
    DIMEN_VALUE,
    FONT_FILE,
    ANIM_FILE,
    RAW_FILE,
    STYLE_THEME_VALUE,
}

data class ResourceRenameOperation(
    override val id: String,
    override val moduleName: String,
    override val path: String,
    val kind: ResourceRenameKind,
    val fileExtension: String?,
    val oldName: String,
    val newName: String,
    override val affectedReferenceCount: Int,
) : PlannedOperation {
    override val category: String = "resource"
    override val itemType: String = kind.name.lowercase().replace('_', ' ')
    override val before: String = oldName
    override val after: String = newName
}

data class ReferenceRewriteOperation(
    override val id: String,
    override val moduleName: String,
    override val path: String,
    val searchText: String,
    val replacementText: String,
    val context: String,
    val fileType: String,
    override val affectedReferenceCount: Int,
) : PlannedOperation {
    override val category: String = "reference"
    override val itemType: String = context
    override val before: String = searchText
    override val after: String = replacementText
}

data class PreviewRow(
    val category: String,
    val type: String,
    val before: String,
    val after: String,
    val moduleName: String,
    val path: String?,
    val references: Int,
)

enum class ExecutionPhase {
    PACKAGE_RENAMES,
    SOURCE_RENAMES,
    RESOURCE_RENAMES,
    REFERENCE_UPDATES,
    REFORMAT_AND_IMPORTS,
    SAVE_AND_REFRESH,
    GIT_COMMIT,
    BUILD_VERIFICATION,
}

enum class PhaseStatus {
    SKIPPED,
    SUCCESS,
    FAILED,
}

data class PhaseResult(
    val phase: ExecutionPhase,
    val status: PhaseStatus,
    val attemptedOperations: Int,
    val successfulOperations: Int,
    val message: String,
    val errors: List<String> = emptyList(),
)

data class GitResult(
    val enabled: Boolean,
    val attempted: Boolean,
    val committed: Boolean,
    val commitHash: String? = null,
    val message: String = "",
)

data class BuildResult(
    val enabled: Boolean,
    val attempted: Boolean,
    val successful: Boolean,
    val summary: String,
)

data class ExecutionReport(
    val request: RefactorRequest,
    val generatedAt: Instant = Instant.now(),
    val dryRun: Boolean,
    val phaseResults: List<PhaseResult>,
    val warnings: List<PlanIssue>,
    val conflicts: List<PlanIssue>,
    val filesChanged: List<String>,
    val gitResult: GitResult,
    val buildResult: BuildResult,
) {
    val hasErrors: Boolean = phaseResults.any { it.status == PhaseStatus.FAILED }
}

enum class ExportFormat {
    JSON,
    MARKDOWN,
}
