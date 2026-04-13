package com.internal.refactorassistant.executor

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.rename.RenameProcessor
import com.internal.refactorassistant.model.BuildResult
import com.internal.refactorassistant.model.ExecutionPhase
import com.internal.refactorassistant.model.ExecutionReport
import com.internal.refactorassistant.model.GitResult
import com.internal.refactorassistant.model.KotlinRenameKind
import com.internal.refactorassistant.model.PhaseResult
import com.internal.refactorassistant.model.PhaseStatus
import com.internal.refactorassistant.model.RefactorPlan
import com.internal.refactorassistant.model.ResourceRenameKind
import com.internal.refactorassistant.settings.InternalRefactorAssistantSettingsService
import com.internal.refactorassistant.vcs.GitCommitService
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class RefactorExecutor(
    private val project: Project,
    private val gitCommitService: GitCommitService = GitCommitService(),
) {
    private val logger = Logger.getInstance(RefactorExecutor::class.java)
    private val psiManager = PsiManager.getInstance(project)
    private val psiDocumentManager = PsiDocumentManager.getInstance(project)
    private val changedPaths = linkedSetOf<String>()
    private val pathMappings = mutableMapOf<String, String>()

    fun execute(plan: RefactorPlan): ExecutionReport {
        val phaseResults = mutableListOf<PhaseResult>()

        val packageResult = executePhase(ExecutionPhase.PACKAGE_RENAMES, plan.packageOperations.size) {
            applyPackageRenames(plan)
        }
        phaseResults += packageResult
        if (packageResult.status == PhaseStatus.FAILED) {
            return buildReport(plan, phaseResults, GitResult(false, false, false, message = "Skipped after package failure"))
        }

        val sourceResult = executePhase(ExecutionPhase.SOURCE_RENAMES, plan.sourceOperations.size) {
            applySourceRenames(plan)
        }
        phaseResults += sourceResult
        if (sourceResult.status == PhaseStatus.FAILED) {
            return buildReport(plan, phaseResults, GitResult(false, false, false, message = "Skipped after source failure"))
        }

        val resourceResult = executePhase(ExecutionPhase.RESOURCE_RENAMES, plan.resourceOperations.size) {
            applyResourceRenames(plan)
        }
        phaseResults += resourceResult
        if (resourceResult.status == PhaseStatus.FAILED) {
            return buildReport(plan, phaseResults, GitResult(false, false, false, message = "Skipped after resource failure"))
        }

        val referenceResult = executePhase(ExecutionPhase.REFERENCE_UPDATES, plan.referenceOperations.size) {
            applyReferenceUpdates(plan)
        }
        phaseResults += referenceResult
        if (referenceResult.status == PhaseStatus.FAILED) {
            return buildReport(plan, phaseResults, GitResult(false, false, false, message = "Skipped after reference failure"))
        }

        val reformatResult = executePhase(ExecutionPhase.REFORMAT_AND_IMPORTS, changedPaths.size) {
            reformatChangedFiles(plan)
        }
        phaseResults += reformatResult
        if (reformatResult.status == PhaseStatus.FAILED) {
            return buildReport(plan, phaseResults, GitResult(false, false, false, message = "Skipped after formatting failure"))
        }

        val saveResult = executePhase(ExecutionPhase.SAVE_AND_REFRESH, changedPaths.size) {
            saveAndRefresh()
        }
        phaseResults += saveResult
        if (saveResult.status == PhaseStatus.FAILED) {
            return buildReport(plan, phaseResults, GitResult(false, false, false, message = "Skipped after save failure"))
        }

        val gitTemplate = InternalRefactorAssistantSettingsService.getInstance().defaults().commitMessageTemplate
        val gitMessage = gitTemplate
            .replace("<oldFeature>", plan.request.oldFeatureName)
            .replace("<newFeature>", plan.request.newFeatureName)
        val gitResult = gitCommitService.commitIfRequested(project, plan.request.options.createGitCommitAfterRefactor, gitMessage)
        phaseResults += PhaseResult(
            phase = ExecutionPhase.GIT_COMMIT,
            status = if (!plan.request.options.createGitCommitAfterRefactor) PhaseStatus.SKIPPED else if (gitResult.committed) PhaseStatus.SUCCESS else PhaseStatus.FAILED,
            attemptedOperations = if (plan.request.options.createGitCommitAfterRefactor) 1 else 0,
            successfulOperations = if (gitResult.committed) 1 else 0,
            message = gitResult.message,
            errors = if (gitResult.committed || gitResult.message.isBlank()) emptyList() else listOf(gitResult.message),
        )

        phaseResults += PhaseResult(
            phase = ExecutionPhase.BUILD_VERIFICATION,
            status = PhaseStatus.SKIPPED,
            attemptedOperations = 0,
            successfulOperations = 0,
            message = if (plan.request.options.runGradleBuildAfterRefactor) {
                "Build verification is a V2 extension point and was not executed."
            } else {
                "Build verification disabled."
            },
        )

        return buildReport(plan, phaseResults, gitResult)
    }

    private fun applyPackageRenames(plan: RefactorPlan) {
        plan.packageOperations.forEach { operation ->
            operation.affectedFilePaths.forEach { originalPath ->
                val currentPath = resolveCurrentPath(originalPath)
                val currentFile = requireVirtualFile(currentPath)
                val targetDir = requireNotNull(
                    VfsUtil.createDirectories("${operation.sourceRootPath}/${operation.newPackage.replace('.', '/')}")
                ) {
                    "Could not create target package directory for ${operation.newPackage}"
                }

                ApplicationManager.getApplication().invokeAndWait {
                    WriteCommandAction.runWriteCommandAction(project) {
                        if (currentFile.parent != targetDir) {
                            currentFile.move(this, targetDir)
                            val newPath = "${targetDir.path}/${currentFile.name}"
                            pathMappings[originalPath] = newPath
                            changedPaths += newPath
                        } else {
                            changedPaths += currentFile.path
                        }

                        val movedFile = requireVirtualFile(resolveCurrentPath(originalPath))
                        val psiFile = requireNotNull(psiManager.findFile(movedFile)) { "Psi not found for ${movedFile.path}" }
                        updatePackageDeclaration(psiFile, operation.newPackage)
                    }
                }
            }
        }
    }

    private fun applySourceRenames(plan: RefactorPlan) {
        plan.sourceOperations.forEach { operation ->
            val psiFile = resolvePsiFile(operation.filePath)
            when (operation.kind) {
                KotlinRenameKind.KOTLIN_CLASS -> {
                    val declaration = (psiFile as? KtFile)
                        ?.declarations
                        ?.filterIsInstance<KtClassOrObject>()
                        ?.firstOrNull { it.name == operation.oldName || it.name == operation.declarationName }
                        ?: error("Kotlin declaration ${operation.oldName} not found in ${psiFile.virtualFile.path}")

                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            RenameProcessor(project, declaration, operation.newName, false, true).run()
                            changedPaths += psiFile.virtualFile.path
                        }
                    }
                }

                KotlinRenameKind.KOTLIN_FILE -> {
                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            RenameProcessor(project, psiFile, operation.newName, false, true).run()
                            val newPath = psiFile.virtualFile.parent.path + "/" + operation.newName
                            pathMappings[operation.filePath] = newPath
                            changedPaths += newPath
                        }
                    }
                }
            }
        }
    }

    private fun applyResourceRenames(plan: RefactorPlan) {
        plan.resourceOperations.forEach { operation ->
            when (operation.kind) {
                ResourceRenameKind.LAYOUT_FILE,
                ResourceRenameKind.DRAWABLE_FILE,
                -> {
                    val psiFile = resolvePsiFile(operation.path)
                    val extension = operation.fileExtension?.takeIf { it.isNotBlank() } ?: FileUtilRt.getExtension(psiFile.name)
                    val newFileName = if (extension.isBlank()) operation.newName else "${operation.newName}.$extension"
                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            RenameProcessor(project, psiFile, newFileName, false, true).run()
                            val newPath = psiFile.virtualFile.parent.path + "/" + newFileName
                            pathMappings[operation.path] = newPath
                            changedPaths += newPath
                        }
                    }
                }

                ResourceRenameKind.STRING_VALUE,
                ResourceRenameKind.DIMEN_VALUE,
                -> {
                    val psiFile = resolvePsiFile(operation.path)
                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            val xmlFile = psiFile as? com.intellij.psi.xml.XmlFile
                                ?: error("Expected XML file for ${operation.path}")
                            val rootTag = xmlFile.rootTag ?: error("XML root tag missing for ${operation.path}")
                            val expectedTagName = if (operation.kind == ResourceRenameKind.STRING_VALUE) "string" else "dimen"
                            val tag = rootTag.subTags.firstOrNull {
                                it.name.equals(expectedTagName, ignoreCase = true) && it.getAttributeValue("name") == operation.oldName
                            } ?: error("Resource ${operation.oldName} not found in ${operation.path}")
                            tag.setAttribute("name", operation.newName)
                            changedPaths += psiFile.virtualFile.path
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    private fun applyReferenceUpdates(plan: RefactorPlan) {
        val grouped = plan.referenceOperations.groupBy { resolveCurrentPath(it.path) }
        grouped.forEach { (path, operations) ->
            val virtualFile = requireVirtualFile(path)
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                ?: error("Document not found for $path")
            ApplicationManager.getApplication().invokeAndWait {
                WriteCommandAction.runWriteCommandAction(project) {
                    var updatedText = document.text
                    operations.forEach { operation ->
                        updatedText = updatedText.replace(operation.searchText, operation.replacementText)
                    }
                    if (updatedText != document.text) {
                        document.setText(updatedText)
                        psiDocumentManager.commitDocument(document)
                        changedPaths += path
                    }
                }
            }
        }
    }

    private fun reformatChangedFiles(plan: RefactorPlan) {
        val psiFiles = changedPaths.mapNotNull { path ->
            LocalFileSystem.getInstance().findFileByPath(path)?.let(psiManager::findFile)
        }
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                psiFiles.forEach { psiFile ->
                    if (plan.request.options.reformatCode) {
                        CodeStyleManager.getInstance(project).reformat(psiFile)
                    }
                }
            }
        }
        if (plan.request.options.optimizeImports && psiFiles.isNotEmpty()) {
            ApplicationManager.getApplication().invokeAndWait {
                OptimizeImportsProcessor(project, psiFiles.toTypedArray(), null).run()
            }
        }
    }

    private fun saveAndRefresh() {
        FileDocumentManager.getInstance().saveAllDocuments()
        VirtualFileManager.getInstance().syncRefresh()
    }

    private fun updatePackageDeclaration(psiFile: PsiFile, newPackage: String) {
        when (psiFile) {
            is KtFile -> {
                val factory = KtPsiFactory(project)
                val newDirective = factory.createFile("package_dummy.kt", "package $newPackage\n").packageDirective
                val oldDirective = psiFile.packageDirective
                when {
                    oldDirective != null && newDirective != null -> oldDirective.replace(newDirective)
                    oldDirective == null && newDirective != null -> psiFile.addBefore(newDirective, psiFile.firstChild)
                }
            }

            is PsiJavaFile -> {
                val elementFactory = com.intellij.psi.JavaPsiFacade.getElementFactory(project)
                val newStatement = elementFactory.createPackageStatement(newPackage)
                val oldStatement = psiFile.packageStatement
                when {
                    oldStatement != null -> oldStatement.replace(newStatement)
                    else -> psiFile.addBefore(newStatement, psiFile.firstChild)
                }
            }
        }
    }

    private fun executePhase(phase: ExecutionPhase, attempted: Int, block: () -> Unit): PhaseResult {
        return runCatching {
            if (attempted == 0) {
                PhaseResult(phase, PhaseStatus.SKIPPED, 0, 0, "No operations planned.")
            } else {
                block()
                PhaseResult(phase, PhaseStatus.SUCCESS, attempted, attempted, "Phase completed successfully.")
            }
        }.getOrElse { throwable ->
            logger.warn("Phase $phase failed", throwable)
            PhaseResult(
                phase = phase,
                status = PhaseStatus.FAILED,
                attemptedOperations = attempted,
                successfulOperations = 0,
                message = throwable.message ?: "Unknown error",
                errors = listOfNotNull(throwable.message),
            )
        }
    }

    private fun buildReport(plan: RefactorPlan, phaseResults: List<PhaseResult>, gitResult: GitResult): ExecutionReport {
        val buildResult = BuildResult(
            enabled = plan.request.options.runGradleBuildAfterRefactor,
            attempted = false,
            successful = false,
            summary = if (plan.request.options.runGradleBuildAfterRefactor) "Skipped in V1." else "Disabled.",
        )
        return ExecutionReport(
            request = plan.request,
            dryRun = false,
            phaseResults = phaseResults,
            warnings = plan.warnings,
            conflicts = plan.conflicts,
            filesChanged = changedPaths.toList().sorted(),
            gitResult = gitResult,
            buildResult = buildResult,
        )
    }

    private fun resolvePsiFile(originalPath: String): PsiFile =
        requireNotNull(psiManager.findFile(requireVirtualFile(resolveCurrentPath(originalPath)))) {
            "Psi file not found for $originalPath"
        }

    private fun requireVirtualFile(path: String) = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(path)) {
        "File not found: $path"
    }

    private fun resolveCurrentPath(originalPath: String): String {
        var current = originalPath
        while (pathMappings.containsKey(current)) {
            current = pathMappings.getValue(current)
        }
        return current
    }
}
