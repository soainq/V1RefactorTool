package com.internal.refactorassistant.scan

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.internal.refactorassistant.model.ModuleSnapshot
import com.internal.refactorassistant.model.RefactorRequest
import com.internal.refactorassistant.model.ReferenceFileSnapshot
import com.internal.refactorassistant.model.ResourceFileSnapshot
import com.internal.refactorassistant.model.ResourceRenameKind
import com.internal.refactorassistant.model.ScanResult
import com.internal.refactorassistant.model.SourceFileSnapshot
import com.internal.refactorassistant.model.SourceLanguage
import com.internal.refactorassistant.model.ValueResourceSnapshot
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class ProjectStructureScanner(
    private val project: Project,
) {
    private val logger = Logger.getInstance(ProjectStructureScanner::class.java)

    fun scan(request: RefactorRequest): ScanResult = ReadAction.compute<ScanResult, RuntimeException> {
        val psiManager = PsiManager.getInstance(project)
        val modules = selectedModules(request)
        val moduleSnapshots = mutableListOf<ModuleSnapshot>()
        val sourceFiles = mutableListOf<SourceFileSnapshot>()
        val resourceFiles = mutableListOf<ResourceFileSnapshot>()
        val valueResources = mutableListOf<ValueResourceSnapshot>()
        val referenceFiles = mutableListOf<ReferenceFileSnapshot>()

        modules.forEach { module ->
            val moduleRoots = ModuleRootManager.getInstance(module)
            val contentRoots = moduleRoots.contentRoots.toList()
            val sourceRoots = moduleRoots.sourceRoots.toList()
            moduleSnapshots += ModuleSnapshot(
                name = module.name,
                contentRoots = contentRoots.map(VirtualFile::getPath),
                sourceRoots = sourceRoots.map(VirtualFile::getPath),
            )

            val rootsToScan = (sourceRoots + contentRoots).distinctBy(VirtualFile::getPath)
            rootsToScan.forEach { root ->
                VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
                    if (file.isDirectory) {
                        return@iterateChildrenRecursively true
                    }

                    when (file.extension?.lowercase()) {
                        "kt" -> {
                            val psiFile = psiManager.findFile(file) as? KtFile
                            if (psiFile != null) {
                                sourceFiles += SourceFileSnapshot(
                                    moduleName = module.name,
                                    path = file.path,
                                    sourceRootPath = findOwningSourceRoot(file, sourceRoots)?.path ?: root.path,
                                    packageName = psiFile.packageFqName.asString(),
                                    fileName = file.name,
                                    classNames = psiFile.declarations.filterIsInstance<KtClassOrObject>().mapNotNull(KtClassOrObject::getName),
                                    language = SourceLanguage.KOTLIN,
                                )
                                referenceFiles += ReferenceFileSnapshot(module.name, file.path, "kt")
                            }
                        }

                        "java" -> {
                            val psiFile = psiManager.findFile(file) as? PsiJavaFile
                            if (psiFile != null) {
                                sourceFiles += SourceFileSnapshot(
                                    moduleName = module.name,
                                    path = file.path,
                                    sourceRootPath = findOwningSourceRoot(file, sourceRoots)?.path ?: root.path,
                                    packageName = psiFile.packageName,
                                    fileName = file.name,
                                    classNames = psiFile.classes.mapNotNull { it.name },
                                    language = SourceLanguage.JAVA,
                                )
                                referenceFiles += ReferenceFileSnapshot(module.name, file.path, "java")
                            }
                        }

                        "xml" -> {
                            referenceFiles += ReferenceFileSnapshot(module.name, file.path, "xml")
                            val psiFile = psiManager.findFile(file) as? XmlFile ?: return@iterateChildrenRecursively true
                            val path = file.path.replace('\\', '/')
                            when {
                                "/res/layout" in path -> resourceFiles += ResourceFileSnapshot(
                                    moduleName = module.name,
                                    path = file.path,
                                    directoryName = file.parent.name,
                                    resourceName = file.nameWithoutExtension,
                                    extension = file.extension.orEmpty(),
                                    kind = ResourceRenameKind.LAYOUT_FILE,
                                )

                                "/res/drawable" in path -> resourceFiles += ResourceFileSnapshot(
                                    moduleName = module.name,
                                    path = file.path,
                                    directoryName = file.parent.name,
                                    resourceName = file.nameWithoutExtension,
                                    extension = file.extension.orEmpty(),
                                    kind = ResourceRenameKind.DRAWABLE_FILE,
                                )

                                "/res/values" in path -> collectValueResources(module.name, file.path, psiFile, valueResources)
                            }
                        }
                    }
                    true
                }
            }
        }

        logger.info(
            "Scan completed: modules=${moduleSnapshots.size}, sourceFiles=${sourceFiles.size}, " +
                "resourceFiles=${resourceFiles.size}, valueResources=${valueResources.size}, referenceFiles=${referenceFiles.size}",
        )

        ScanResult(
            modules = moduleSnapshots,
            sourceFiles = sourceFiles,
            resourceFiles = resourceFiles,
            valueResources = valueResources,
            referenceFiles = referenceFiles.distinctBy { it.path },
        )
    }

    private fun collectValueResources(
        moduleName: String,
        path: String,
        xmlFile: XmlFile,
        target: MutableList<ValueResourceSnapshot>,
    ) {
        val rootTag = xmlFile.rootTag ?: return
        rootTag.subTags.forEach { tag ->
            val name = tag.getAttributeValue("name") ?: return@forEach
            when (tag.name) {
                "string" -> target += ValueResourceSnapshot(
                    moduleName = moduleName,
                    xmlFilePath = path,
                    tagName = tag.name,
                    resourceName = name,
                    kind = ResourceRenameKind.STRING_VALUE,
                )

                "dimen" -> target += ValueResourceSnapshot(
                    moduleName = moduleName,
                    xmlFilePath = path,
                    tagName = tag.name,
                    resourceName = name,
                    kind = ResourceRenameKind.DIMEN_VALUE,
                )
            }
        }
    }

    private fun selectedModules(request: RefactorRequest): List<Module> {
        val allModules = ModuleManager.getInstance(project).modules.toList()
        if (request.selectedModules.isEmpty()) {
            return allModules
        }
        val selected = allModules.filter { it.name in request.selectedModules }
        return if (selected.isNotEmpty()) selected else allModules
    }

    private fun findOwningSourceRoot(file: VirtualFile, sourceRoots: List<VirtualFile>): VirtualFile? =
        sourceRoots.firstOrNull { VfsUtilCore.isAncestor(it, file, false) }
}
