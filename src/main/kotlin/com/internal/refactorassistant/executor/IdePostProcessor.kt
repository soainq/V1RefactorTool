package com.internal.refactorassistant.executor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.nio.file.Path

class IdePostProcessor {
    fun reformat(project: Project, changedFiles: List<String>) {
        if (changedFiles.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            val psiManager = PsiManager.getInstance(project)
            val codeStyleManager = CodeStyleManager.getInstance(project)
            changedFiles.forEach { path ->
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return@forEach
                val psiFile = psiManager.findFile(virtualFile) ?: return@forEach
                runCatching { codeStyleManager.reformat(psiFile) }
            }
            FileDocumentManager.getInstance().saveAllDocuments()
        }

        changedFiles.forEach { path ->
            LocalFileSystem.getInstance().refreshAndFindFileByPath(Path.of(path).toString())
        }
    }
}
