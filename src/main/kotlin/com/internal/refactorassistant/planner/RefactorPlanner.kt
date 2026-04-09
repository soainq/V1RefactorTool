package com.internal.refactorassistant.planner

import com.intellij.openapi.diagnostic.Logger
import com.internal.refactorassistant.model.IssueSeverity
import com.internal.refactorassistant.model.KotlinRenameKind
import com.internal.refactorassistant.model.KotlinRenameOperation
import com.internal.refactorassistant.model.PackageRenameOperation
import com.internal.refactorassistant.model.PlanCounts
import com.internal.refactorassistant.model.PlanIssue
import com.internal.refactorassistant.model.PreviewRow
import com.internal.refactorassistant.model.RefactorPlan
import com.internal.refactorassistant.model.RefactorRequest
import com.internal.refactorassistant.model.ReferenceRewriteOperation
import com.internal.refactorassistant.model.ResourceRenameKind
import com.internal.refactorassistant.model.ResourceRenameOperation
import com.internal.refactorassistant.model.RenameTargetKind
import com.internal.refactorassistant.model.ScanResult
import com.internal.refactorassistant.model.SourceLanguage
import com.internal.refactorassistant.rules.NamingRuleEngine
import com.internal.refactorassistant.util.ValidationUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

class RefactorPlanner(
    private val namingRuleEngine: NamingRuleEngine,
) {
    private val logger = Logger.getInstance(RefactorPlanner::class.java)

    fun buildPlan(request: RefactorRequest, scanResult: ScanResult): RefactorPlan {
        val conflicts = mutableListOf<PlanIssue>()
        val warnings = mutableListOf<PlanIssue>()

        appendV1Warnings(request, warnings)

        val packageOperations = if (request.options.renamePackages) {
            buildPackageOperations(request, scanResult, conflicts)
        } else {
            emptyList()
        }

        val sourceOperations = if (request.options.renameClassesAndFiles) {
            buildSourceOperations(scanResult, conflicts)
        } else {
            emptyList()
        }

        val resourceOperations = buildResourceOperations(request, scanResult, conflicts)
        val referenceOperations = buildReferenceOperations(scanResult, packageOperations, resourceOperations)

        val counts = PlanCounts(
            packageOperations = packageOperations.size,
            sourceOperations = sourceOperations.size,
            resourceOperations = resourceOperations.size,
            referenceOperations = referenceOperations.size,
            conflicts = conflicts.size,
            warnings = warnings.size,
            affectedFiles = distinctAffectedFiles(packageOperations, sourceOperations, resourceOperations, referenceOperations),
            affectedReferences = (
                packageOperations.sumOf { it.affectedReferenceCount } +
                    sourceOperations.sumOf { it.affectedReferenceCount } +
                    resourceOperations.sumOf { it.affectedReferenceCount } +
                    referenceOperations.sumOf { it.affectedReferenceCount }
                ),
        )

        logger.info(
            "Plan generated: packageOps=${counts.packageOperations}, sourceOps=${counts.sourceOperations}, " +
                "resourceOps=${counts.resourceOperations}, referenceOps=${counts.referenceOperations}, " +
                "warnings=${counts.warnings}, conflicts=${counts.conflicts}",
        )

        return RefactorPlan(
            request = request,
            packageOperations = packageOperations,
            sourceOperations = sourceOperations,
            resourceOperations = resourceOperations,
            referenceOperations = referenceOperations,
            conflicts = conflicts,
            warnings = warnings,
            counts = counts,
        )
    }

    fun previewRows(plan: RefactorPlan): List<PreviewRow> = buildList {
        addAll(plan.packageOperations.map { it.toPreviewRow() })
        addAll(plan.sourceOperations.map { it.toPreviewRow() })
        addAll(plan.resourceOperations.map { it.toPreviewRow() })
        addAll(plan.referenceOperations.map { it.toPreviewRow() })
    }.sortedWith(compareBy(PreviewRow::category, PreviewRow::type, PreviewRow::before))

    private fun buildPackageOperations(
        request: RefactorRequest,
        scanResult: ScanResult,
        conflicts: MutableList<PlanIssue>,
    ): List<PackageRenameOperation> {
        val grouped = scanResult.sourceFiles
            .filter { it.packageName == request.oldPackagePrefix || it.packageName.startsWith("${request.oldPackagePrefix}.") }
            .groupBy { Triple(it.moduleName, it.sourceRootPath, it.packageName) }

        return grouped.mapNotNull { (key, files) ->
            val (_, sourceRootPath, oldPackage) = key
            val newPackage = oldPackage.replaceFirst(request.oldPackagePrefix, request.newPackagePrefix)
            if (!ValidationUtil.isValidPackageName(newPackage)) {
                conflicts += PlanIssue(
                    severity = IssueSeverity.ERROR,
                    code = "INVALID_PACKAGE",
                    message = "Invalid target package name: $newPackage",
                    path = sourceRootPath,
                    before = oldPackage,
                    after = newPackage,
                )
                return@mapNotNull null
            }

            files.forEach { file ->
                val target = Path.of(sourceRootPath, newPackage.replace('.', '/'), file.fileName)
                if (Files.exists(target) && file.path != target.toString()) {
                    conflicts += PlanIssue(
                        severity = IssueSeverity.ERROR,
                        code = "PACKAGE_COLLISION",
                        message = "Package move would overwrite ${target.name}",
                        path = target.toString(),
                        before = file.path,
                        after = target.toString(),
                    )
                }
            }

            PackageRenameOperation(
                id = "pkg-${key.first}-${oldPackage}",
                moduleName = key.first,
                sourceRootPath = sourceRootPath,
                oldPackage = oldPackage,
                newPackage = newPackage,
                affectedFilePaths = files.map { it.path },
                affectedReferenceCount = countLiteralReferences(scanResult, oldPackage),
            )
        }
    }

    private fun buildSourceOperations(
        scanResult: ScanResult,
        conflicts: MutableList<PlanIssue>,
    ): List<KotlinRenameOperation> {
        val operations = mutableListOf<KotlinRenameOperation>()

        scanResult.sourceFiles
            .filter { it.language == SourceLanguage.KOTLIN }
            .forEach { sourceFile ->
                sourceFile.classNames.forEach { className ->
                    val renamed = namingRuleEngine.renameClassName(className)
                    if (namingRuleEngine.wouldChange(className, renamed)) {
                        if (!ValidationUtil.isValidIdentifier(renamed)) {
                            conflicts += PlanIssue(
                                severity = IssueSeverity.ERROR,
                                code = "INVALID_IDENTIFIER",
                                message = "Invalid target class name: $renamed",
                                path = sourceFile.path,
                                before = className,
                                after = renamed,
                            )
                        } else {
                            operations += KotlinRenameOperation(
                                id = "src-class-${sourceFile.path}-$className",
                                moduleName = sourceFile.moduleName,
                                filePath = sourceFile.path,
                                packageName = sourceFile.packageName,
                                declarationName = className,
                                kind = KotlinRenameKind.KOTLIN_CLASS,
                                oldName = className,
                                newName = renamed,
                                affectedReferenceCount = countLiteralReferences(scanResult, className),
                            )
                        }
                    }
                }

                val fileStem = sourceFile.fileName.substringBeforeLast('.')
                val renamedFileStem = namingRuleEngine.renameFileStem(fileStem)
                if (namingRuleEngine.wouldChange(fileStem, renamedFileStem)) {
                    val newFileName = "$renamedFileStem.${sourceFile.fileName.substringAfterLast('.')}"
                    operations += KotlinRenameOperation(
                        id = "src-file-${sourceFile.path}",
                        moduleName = sourceFile.moduleName,
                        filePath = sourceFile.path,
                        packageName = sourceFile.packageName,
                        declarationName = null,
                        kind = KotlinRenameKind.KOTLIN_FILE,
                        oldName = sourceFile.fileName,
                        newName = newFileName,
                        affectedReferenceCount = countLiteralReferences(scanResult, fileStem),
                    )
                }
            }

        return operations
    }

    private fun buildResourceOperations(
        request: RefactorRequest,
        scanResult: ScanResult,
        conflicts: MutableList<PlanIssue>,
    ): List<ResourceRenameOperation> {
        val operations = mutableListOf<ResourceRenameOperation>()

        scanResult.resourceFiles.forEach { resourceFile ->
            val targetKind = when (resourceFile.kind) {
                ResourceRenameKind.LAYOUT_FILE -> RenameTargetKind.LAYOUT
                ResourceRenameKind.DRAWABLE_FILE -> RenameTargetKind.DRAWABLE
                else -> return@forEach
            }
            if ((resourceFile.kind == ResourceRenameKind.LAYOUT_FILE && !request.options.renameLayouts) ||
                (resourceFile.kind == ResourceRenameKind.DRAWABLE_FILE && !request.options.renameDrawables)
            ) {
                return@forEach
            }

            val renamed = namingRuleEngine.renameResourceName(resourceFile.resourceName, targetKind)
            if (namingRuleEngine.wouldChange(resourceFile.resourceName, renamed)) {
                if (!ValidationUtil.isValidAndroidResourceName(renamed)) {
                    conflicts += PlanIssue(
                        severity = IssueSeverity.ERROR,
                        code = "INVALID_RESOURCE",
                        message = "Invalid Android resource name: $renamed",
                        path = resourceFile.path,
                        before = resourceFile.resourceName,
                        after = renamed,
                    )
                } else {
                    operations += ResourceRenameOperation(
                        id = "res-file-${resourceFile.path}",
                        moduleName = resourceFile.moduleName,
                        path = resourceFile.path,
                        kind = resourceFile.kind,
                        fileExtension = resourceFile.extension,
                        oldName = resourceFile.resourceName,
                        newName = renamed,
                        affectedReferenceCount = countResourceReferences(scanResult, resourceFile.kind, resourceFile.resourceName),
                    )
                }
            }
        }

        scanResult.valueResources.forEach { valueResource ->
            val enabled = when (valueResource.kind) {
                ResourceRenameKind.STRING_VALUE -> request.options.renameStrings
                ResourceRenameKind.DIMEN_VALUE -> request.options.renameDimens
                else -> false
            }
            if (!enabled) return@forEach

            val targetKind = when (valueResource.kind) {
                ResourceRenameKind.STRING_VALUE -> RenameTargetKind.STRING
                ResourceRenameKind.DIMEN_VALUE -> RenameTargetKind.DIMEN
                else -> return@forEach
            }
            val renamed = namingRuleEngine.renameResourceName(valueResource.resourceName, targetKind)
            if (namingRuleEngine.wouldChange(valueResource.resourceName, renamed)) {
                if (!ValidationUtil.isValidAndroidResourceName(renamed)) {
                    conflicts += PlanIssue(
                        severity = IssueSeverity.ERROR,
                        code = "INVALID_RESOURCE",
                        message = "Invalid Android resource name: $renamed",
                        path = valueResource.xmlFilePath,
                        before = valueResource.resourceName,
                        after = renamed,
                    )
                } else {
                    operations += ResourceRenameOperation(
                        id = "res-value-${valueResource.xmlFilePath}-${valueResource.resourceName}",
                        moduleName = valueResource.moduleName,
                        path = valueResource.xmlFilePath,
                        kind = valueResource.kind,
                        fileExtension = null,
                        oldName = valueResource.resourceName,
                        newName = renamed,
                        affectedReferenceCount = countResourceReferences(scanResult, valueResource.kind, valueResource.resourceName),
                    )
                }
            }
        }

        return operations
    }

    private fun buildReferenceOperations(
        scanResult: ScanResult,
        packageOperations: List<PackageRenameOperation>,
        resourceOperations: List<ResourceRenameOperation>,
    ): List<ReferenceRewriteOperation> {
        val operations = mutableListOf<ReferenceRewriteOperation>()

        packageOperations.forEach { operation ->
            scanResult.referenceFiles.forEach { referenceFile ->
                val occurrences = countOccurrences(referenceFile.path, operation.oldPackage)
                if (occurrences > 0) {
                    operations += ReferenceRewriteOperation(
                        id = "ref-pkg-${referenceFile.path}-${operation.oldPackage}",
                        moduleName = referenceFile.moduleName,
                        path = referenceFile.path,
                        searchText = operation.oldPackage,
                        replacementText = operation.newPackage,
                        context = "package reference",
                        fileType = referenceFile.fileType,
                        affectedReferenceCount = occurrences,
                    )
                }
            }
        }

        resourceOperations.forEach { operation ->
            referenceForms(operation).forEach { (search, replacement) ->
                scanResult.referenceFiles.forEach { referenceFile ->
                    val occurrences = countOccurrences(referenceFile.path, search)
                    if (occurrences > 0) {
                        operations += ReferenceRewriteOperation(
                            id = "ref-res-${referenceFile.path}-${operation.oldName}-$search",
                            moduleName = referenceFile.moduleName,
                            path = referenceFile.path,
                            searchText = search,
                            replacementText = replacement,
                            context = "${operation.kind.name.lowercase()} reference",
                            fileType = referenceFile.fileType,
                            affectedReferenceCount = occurrences,
                        )
                    }
                }
            }
        }

        return operations.distinctBy { listOf(it.path, it.searchText, it.replacementText, it.context).joinToString("|") }
    }

    private fun referenceForms(operation: ResourceRenameOperation): List<Pair<String, String>> = when (operation.kind) {
        ResourceRenameKind.LAYOUT_FILE -> listOf(
            "R.layout.${operation.oldName}" to "R.layout.${operation.newName}",
            "@layout/${operation.oldName}" to "@layout/${operation.newName}",
        )

        ResourceRenameKind.DRAWABLE_FILE -> listOf(
            "R.drawable.${operation.oldName}" to "R.drawable.${operation.newName}",
            "@drawable/${operation.oldName}" to "@drawable/${operation.newName}",
        )

        ResourceRenameKind.STRING_VALUE -> listOf(
            "R.string.${operation.oldName}" to "R.string.${operation.newName}",
            "@string/${operation.oldName}" to "@string/${operation.newName}",
        )

        ResourceRenameKind.DIMEN_VALUE -> listOf(
            "R.dimen.${operation.oldName}" to "R.dimen.${operation.newName}",
            "@dimen/${operation.oldName}" to "@dimen/${operation.newName}",
        )

        else -> emptyList()
    }

    private fun countLiteralReferences(scanResult: ScanResult, literal: String): Int =
        scanResult.referenceFiles.sumOf { countOccurrences(it.path, literal) }

    private fun countResourceReferences(scanResult: ScanResult, kind: ResourceRenameKind, name: String): Int = when (kind) {
        ResourceRenameKind.LAYOUT_FILE -> countLiteralReferences(scanResult, "R.layout.$name") + countLiteralReferences(scanResult, "@layout/$name")
        ResourceRenameKind.DRAWABLE_FILE -> countLiteralReferences(scanResult, "R.drawable.$name") + countLiteralReferences(scanResult, "@drawable/$name")
        ResourceRenameKind.STRING_VALUE -> countLiteralReferences(scanResult, "R.string.$name") + countLiteralReferences(scanResult, "@string/$name")
        ResourceRenameKind.DIMEN_VALUE -> countLiteralReferences(scanResult, "R.dimen.$name") + countLiteralReferences(scanResult, "@dimen/$name")
        else -> 0
    }

    private fun countOccurrences(path: String, literal: String): Int {
        val filePath = Path.of(path)
        if (!Files.exists(filePath)) return 0
        val text = runCatching { filePath.readText() }.getOrDefault("")
        if (text.isBlank() || literal.isBlank()) return 0
        return Regex(Regex.escape(literal)).findAll(text).count()
    }

    private fun distinctAffectedFiles(
        packageOperations: List<PackageRenameOperation>,
        sourceOperations: List<KotlinRenameOperation>,
        resourceOperations: List<ResourceRenameOperation>,
        referenceOperations: List<ReferenceRewriteOperation>,
    ): Int = buildSet {
        packageOperations.forEach { addAll(it.affectedFilePaths) }
        sourceOperations.forEach { add(it.filePath) }
        resourceOperations.forEach { add(it.path) }
        referenceOperations.forEach { add(it.path) }
    }.size

    private fun appendV1Warnings(request: RefactorRequest, warnings: MutableList<PlanIssue>) {
        if (request.options.renameVariablesAndFunctions) {
            warnings += PlanIssue(
                severity = IssueSeverity.WARNING,
                code = "V2_VARIABLES",
                message = "Variable/function rename is exposed in the UI but deferred in V1 unless explicit deterministic rules are added later.",
            )
        }
        if (request.options.renameFonts || request.options.renameAnim || request.options.renameRaw || request.options.renameStylesThemes) {
            warnings += PlanIssue(
                severity = IssueSeverity.WARNING,
                code = "V2_RESOURCE_GROUPS",
                message = "Font, anim, raw, and style/theme rename groups are reserved as V2 extension points in this milestone.",
            )
        }
        if (request.options.runGradleBuildAfterRefactor) {
            warnings += PlanIssue(
                severity = IssueSeverity.WARNING,
                code = "V2_BUILD",
                message = "Build verification is wired as an extension point and reported as skipped in V1.",
            )
        }
    }
}

private fun PackageRenameOperation.toPreviewRow(): PreviewRow = PreviewRow(
    category = category,
    type = itemType,
    before = before,
    after = after,
    moduleName = moduleName,
    path = path,
    references = affectedReferenceCount,
)

private fun KotlinRenameOperation.toPreviewRow(): PreviewRow = PreviewRow(
    category = category,
    type = itemType,
    before = before,
    after = after,
    moduleName = moduleName,
    path = path,
    references = affectedReferenceCount,
)

private fun ResourceRenameOperation.toPreviewRow(): PreviewRow = PreviewRow(
    category = category,
    type = itemType,
    before = before,
    after = after,
    moduleName = moduleName,
    path = path,
    references = affectedReferenceCount,
)

private fun ReferenceRewriteOperation.toPreviewRow(): PreviewRow = PreviewRow(
    category = category,
    type = itemType,
    before = before,
    after = after,
    moduleName = moduleName,
    path = path,
    references = affectedReferenceCount,
)
