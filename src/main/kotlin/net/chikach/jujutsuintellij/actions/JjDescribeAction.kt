package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.JjWorkingCopyDescription
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Opens a dialog pre-filled with the current description of `@` and saves it via `jj describe -m`.
 */
class JjDescribeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        val current = loadCurrentDescription(project, repo)
        val dialog = DescribeDialog(project, current)
        if (!dialog.showAndGet()) return

        val newMessage = dialog.message
        if (newMessage.isBlank()) return

        object : Task.Backgroundable(project, "Describing Working Copy") {
            override fun run(indicator: ProgressIndicator) {
                val result = JjCommands.getInstance().describe(repo, newMessage)
                if (!result.isSuccess) {
                    throw RuntimeException(result.stderr.trim().ifEmpty { "jj describe failed (exit ${result.exitCode})" })
                }
                JjWorkingCopyDescription.getInstance(project).refresh()
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "jj describe Failed")
            }
        }.queue()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && findRepo(e) != null
    }

    private fun loadCurrentDescription(project: Project, repo: JjRepository): String {
        var description = ""
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                val result = JjCommands.getInstance().getDescription(repo)
                if (result.isSuccess) description = result.stdout.trimEnd()
            },
            "Loading Current Description",
            false,
            project,
        )
        return description
    }

    private fun findRepo(e: AnActionEvent): JjRepository? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val manager = JjRepositoryManager.getInstance(project)
        return if (file != null) manager.getRepositoryForFile(file)
        else manager.getAll().firstOrNull()
    }

    private class DescribeDialog(project: Project, initialText: String) : DialogWrapper(project) {
        private val textArea = JBTextArea(initialText, 8, 60).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        val message: String get() = textArea.text

        init {
            title = "Describe Working Copy"
            init()
        }

        override fun createCenterPanel(): JComponent =
            JBScrollPane(textArea).apply {
                preferredSize = Dimension(600, 200)
            }

        override fun getPreferredFocusedComponent(): JComponent = textArea
    }
}
