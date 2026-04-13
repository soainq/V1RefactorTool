package com.internal.refactorassistant.executor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.nio.file.Path

class IdePostProcessor {
    fun syncAfterApply(project: Project, changedFiles: List<String>) {
        if (changedFiles.isEmpty()) return

        val localFileSystem = LocalFileSystem.getInstance()
        val fileDocumentManager = FileDocumentManager.getInstance()
        val psiDocumentManager = PsiDocumentManager.getInstance(project)

        changedFiles.forEach { path ->
            val virtualFile = localFileSystem.refreshAndFindFileByPath(path) ?: return@forEach
            virtualFile.refresh(false, false)
            fileDocumentManager.getDocument(virtualFile)?.let { document ->
                if (fileDocumentManager.isFileModified(virtualFile)) {
                    psiDocumentManager.commitDocument(document)
                    fileDocumentManager.saveDocument(document)
                } else {
                    fileDocumentManager.reloadFromDisk(document)
                }
            }
        }
        psiDocumentManager.commitAllDocuments()
        fileDocumentManager.saveAllDocuments()
    }

    fun reformat(project: Project, changedFiles: List<String>) {
        if (changedFiles.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            val psiManager = PsiManager.getInstance(project)
            val codeStyleManager = CodeStyleManager.getInstance(project)
            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            changedFiles.forEach { path ->
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return@forEach
                val psiFile = psiManager.findFile(virtualFile) ?: return@forEach
                runCatching { codeStyleManager.reformat(psiFile) }
                psiDocumentManager.getDocument(psiFile)?.let(psiDocumentManager::commitDocument)
            }
            FileDocumentManager.getInstance().saveAllDocuments()
        }

        changedFiles.forEach { path ->
            LocalFileSystem.getInstance().refreshAndFindFileByPath(Path.of(path).toString())
        }
    }
}
