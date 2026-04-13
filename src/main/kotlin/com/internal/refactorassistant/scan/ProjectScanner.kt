package com.internal.refactorassistant.scan

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.internal.refactorassistant.model.ItemDetails
import com.internal.refactorassistant.model.ProjectScanResult
import com.internal.refactorassistant.model.RefactorItemType
import com.internal.refactorassistant.model.ScanSettings
import com.internal.refactorassistant.model.ScannedRefactorItem
import com.internal.refactorassistant.model.SafetyLevel
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class ProjectScanner {
    private val ignoredDirectories = setOf("build", ".gradle", ".idea", ".git", ".project-refactor")
    private val kotlinClassPattern = Pattern.compile(
        "\\b(?:(?:data|sealed|enum)\\s+class|class|interface|object)\\s+([A-Za-z_][A-Za-z0-9_]*)"
    )
    private val packagePattern = Pattern.compile("^\\s*package\\s+([A-Za-z0-9_.]+)", Pattern.MULTILINE)
    private val stringPattern = Pattern.compile("<string\\s+name\\s*=\\s*\"([a-zA-Z0-9_]+)\"")
    private val dimenPattern = Pattern.compile("<dimen\\s+name\\s*=\\s*\"([a-zA-Z0-9_]+)\"")

    fun scan(project: Project, settings: ScanSettings): ProjectScanResult = ReadAction.compute<ProjectScanResult, RuntimeException> {
        val modules = selectedModules(project, settings.selectedModules)
        val items = mutableListOf<ScannedRefactorItem>()
        val packageRecords = mutableListOf<PackageRecord>()
        val existingNames = mutableMapOf<RefactorItemType, MutableSet<String>>()
        val referenceFiles = linkedSetOf<String>()

        modules.forEach { module ->
            val moduleRoots = ModuleRootManager.getInstance(module)
            val sourceRoots = moduleRoots.sourceRoots.toList()
            val contentRoots = moduleRoots.contentRoots.toList()
            val roots = (sourceRoots + contentRoots).distinctBy(VirtualFile::getPath)

            roots.forEach { root ->
                VfsUtilCore.iterateChildrenRecursively(root, { file -> !shouldSkipDirectory(file) }) { file ->
                    if (!file.isDirectory) {
                        when {
                            file.extension.equals("kt", ignoreCase = true) -> {
                                val text = file.inputStream.reader(Charsets.UTF_8).readText()
                                referenceFiles += file.path
                                val packageName = packagePattern.matcher(text).let { matcher ->
                                    if (matcher.find()) matcher.group(1) else ""
                                }
                                val sourceRoot = sourceRoots.firstOrNull { VfsUtilCore.isAncestor(it, file, false) }?.path.orEmpty()
                                if (packageName.isNotBlank()) {
                                    packageRecords += PackageRecord(module.name, packageName, file.path, sourceRoot)
                                }
                                if (settings.scanKotlinClassesFiles) {
                                    items += ScannedRefactorItem(
                                        id = UUID.randomUUID().toString(),
                                        type = RefactorItemType.KOTLIN_FILE,
                                        oldName = file.nameWithoutExtension,
                                        displayPath = file.path,
                                        absolutePath = file.path,
                                        moduleName = module.name,
                                        safetyLevel = SafetyLevel.REVIEW_REQUIRED,
                                        details = ItemDetails(packageName = packageName, sourceRootPath = sourceRoot),
                                    )
                                    existingNames.getOrPut(RefactorItemType.KOTLIN_FILE) { linkedSetOf() } += file.nameWithoutExtension
                                }

                                kotlinClassPattern.matcher(text).let { matcher ->
                                    while (matcher.find()) {
                                        val className = matcher.group(1)
                                        val type = detectClassType(className)
                                        if (shouldIncludeClass(type, settings)) {
                                            items += ScannedRefactorItem(
                                                id = UUID.randomUUID().toString(),
                                                type = type,
                                                oldName = className,
                                                displayPath = file.path,
                                                absolutePath = file.path,
                                                moduleName = module.name,
                                                safetyLevel = SafetyLevel.REVIEW_REQUIRED,
                                                details = ItemDetails(packageName = packageName, sourceRootPath = sourceRoot),
                                            )
                                            existingNames.getOrPut(type) { linkedSetOf() } += className
                                        }
                                    }
                                }
                            }

                            isLayoutFile(file) && settings.scanLayouts -> {
                                items += ScannedRefactorItem(
                                    id = UUID.randomUUID().toString(),
                                    type = RefactorItemType.LAYOUT,
                                    oldName = file.nameWithoutExtension,
                                    displayPath = file.path,
                                    absolutePath = file.path,
                                    moduleName = module.name,
                                    safetyLevel = SafetyLevel.REVIEW_REQUIRED,
                                    details = ItemDetails(resFolderName = file.parent.name),
                                )
                                existingNames.getOrPut(RefactorItemType.LAYOUT) { linkedSetOf() } += file.nameWithoutExtension
                                referenceFiles += file.path
                            }

                            isDrawableFile(file) && settings.scanDrawables -> {
                                items += ScannedRefactorItem(
                                    id = UUID.randomUUID().toString(),
                                    type = RefactorItemType.DRAWABLE,
                                    oldName = file.nameWithoutExtension,
                                    displayPath = file.path,
                                    absolutePath = file.path,
                                    moduleName = module.name,
                                    safetyLevel = SafetyLevel.REVIEW_REQUIRED,
                                    details = ItemDetails(resFolderName = file.parent.name),
                                )
                                existingNames.getOrPut(RefactorItemType.DRAWABLE) { linkedSetOf() } += file.nameWithoutExtension
                                referenceFiles += file.path
                            }

                            isValuesXml(file) -> {
                                val text = file.inputStream.reader(Charsets.UTF_8).readText()
                                referenceFiles += file.path
                                if (settings.scanStrings) {
                                    stringPattern.matcher(text).let { matcher ->
                                        while (matcher.find()) {
                                            val name = matcher.group(1)
                                            items += ScannedRefactorItem(
                                                id = UUID.randomUUID().toString(),
                                                type = RefactorItemType.STRING,
                                                oldName = name,
                                                displayPath = file.path,
                                                absolutePath = file.path,
                                                moduleName = module.name,
                                                safetyLevel = SafetyLevel.REVIEW_REQUIRED,
                                                details = ItemDetails(xmlFilePath = file.path, xmlTagName = "string"),
                                            )
                                            existingNames.getOrPut(RefactorItemType.STRING) { linkedSetOf() } += name
                                        }
                                    }
                                }
                                if (settings.scanDimens) {
                                    dimenPattern.matcher(text).let { matcher ->
                                        while (matcher.find()) {
                                            val name = matcher.group(1)
                                            items += ScannedRefactorItem(
                                                id = UUID.randomUUID().toString(),
                                                type = RefactorItemType.DIMEN,
                                                oldName = name,
                                                displayPath = file.path,
                                                absolutePath = file.path,
                                                moduleName = module.name,
                                                safetyLevel = SafetyLevel.REVIEW_REQUIRED,
                                                details = ItemDetails(xmlFilePath = file.path, xmlTagName = "dimen"),
                                            )
                                            existingNames.getOrPut(RefactorItemType.DIMEN) { linkedSetOf() } += name
                                        }
                                    }
                                }
                            }

                            file.name == "AndroidManifest.xml" || file.extension.equals("xml", ignoreCase = true) || file.extension.equals("java", ignoreCase = true) -> {
                                referenceFiles += file.path
                            }
                        }
                    }
                    true
                }
            }
        }

        val packageNames = packageRecords.map { it.packageName }.distinct()
        val rootPackage = commonRootPackage(packageNames)
        if (settings.scanFeaturePackages) {
            packageRecords
                .mapNotNull { record -> packageItem(record, rootPackage) }
                .distinctBy { listOf(it.oldName, it.moduleName, it.details.sourceRootPath).joinToString("|") }
                .forEach { item ->
                    items += item
                    existingNames.getOrPut(RefactorItemType.PACKAGE_CHILD) { linkedSetOf() } += item.oldName
                }
        }

        ProjectScanResult(
            moduleNames = modules.map(Module::getName),
            rootPackage = rootPackage,
            items = items.distinctBy { listOf(it.type, it.oldName, it.absolutePath, it.moduleName).joinToString("|") },
            existingNamesByType = existingNames.mapValues { (_, value) -> value.toSet() },
            referenceFiles = referenceFiles.toList().sorted(),
        )
    }

    private fun selectedModules(project: Project, selectedModules: List<String>): List<Module> {
        val modules = ModuleManager.getInstance(project).modules.toList()
        if (selectedModules.isEmpty()) return modules
        return modules.filter { it.name in selectedModules }.ifEmpty { modules }
    }

    private fun shouldSkipDirectory(file: VirtualFile): Boolean =
        file.isDirectory && file.name in ignoredDirectories

    private fun detectClassType(className: String): RefactorItemType = when {
        className.endsWith("Activity") -> RefactorItemType.ACTIVITY
        className.endsWith("Fragment") -> RefactorItemType.FRAGMENT
        className.endsWith("ViewModel") -> RefactorItemType.VIEWMODEL
        className.endsWith("Adapter") -> RefactorItemType.ADAPTER
        else -> RefactorItemType.KOTLIN_CLASS
    }

    private fun shouldIncludeClass(type: RefactorItemType, settings: ScanSettings): Boolean = when (type) {
        RefactorItemType.ACTIVITY -> settings.scanActivities
        RefactorItemType.FRAGMENT -> settings.scanFragments
        RefactorItemType.VIEWMODEL -> settings.scanViewModels
        RefactorItemType.ADAPTER -> settings.scanAdapters
        RefactorItemType.KOTLIN_CLASS -> settings.scanKotlinClassesFiles
        else -> false
    }

    private fun isLayoutFile(file: VirtualFile): Boolean {
        val path = file.path.replace('\\', '/').lowercase(Locale.US)
        return file.extension.equals("xml", ignoreCase = true) && "/res/layout" in path
    }

    private fun isDrawableFile(file: VirtualFile): Boolean {
        val path = file.path.replace('\\', '/').lowercase(Locale.US)
        return "/res/drawable" in path || "/res/mipmap" in path
    }

    private fun isValuesXml(file: VirtualFile): Boolean {
        val path = file.path.replace('\\', '/').lowercase(Locale.US)
        return file.extension.equals("xml", ignoreCase = true) && "/res/values" in path
    }

    private fun commonRootPackage(packageNames: List<String>): String? {
        if (packageNames.isEmpty()) return null
        val split = packageNames.map { it.split('.') }
        val commonSegments = mutableListOf<String>()
        val minLength = split.minOf { it.size }
        for (index in 0 until minLength) {
            val segment = split.first()[index]
            if (split.all { it[index] == segment }) {
                commonSegments += segment
            } else {
                break
            }
        }
        if (commonSegments.isEmpty()) return null
        return if (commonSegments.size >= 3) commonSegments.take(3).joinToString(".") else commonSegments.joinToString(".")
    }

    private fun packageItem(record: PackageRecord, rootPackage: String?): ScannedRefactorItem? {
        if (rootPackage.isNullOrBlank()) return null
        if (record.packageName == rootPackage || !record.packageName.startsWith("$rootPackage.")) return null
        val relative = record.packageName.removePrefix("$rootPackage.")
        return ScannedRefactorItem(
            id = UUID.randomUUID().toString(),
            type = RefactorItemType.PACKAGE_CHILD,
            oldName = record.packageName,
            displayPath = record.packageName,
            absolutePath = packageDirectoryPath(record.sourceRootPath, record.packageName),
            moduleName = record.moduleName,
            safetyLevel = SafetyLevel.REVIEW_REQUIRED,
            details = ItemDetails(
                packageName = record.packageName,
                rootPackage = rootPackage,
                packageRelativePath = relative,
                sourceRootPath = record.sourceRootPath,
            ),
        )
    }

    private fun packageDirectoryPath(sourceRootPath: String, packageName: String): String =
        if (sourceRootPath.isBlank()) packageName else "$sourceRootPath/${packageName.replace('.', '/')}"

    private data class PackageRecord(
        val moduleName: String,
        val packageName: String,
        val filePath: String,
        val sourceRootPath: String,
    )
}
