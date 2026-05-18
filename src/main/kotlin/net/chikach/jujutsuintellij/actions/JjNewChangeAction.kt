package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.JjWorkingCopyCache

/**
 * Runs `jj new` to open a fresh working-copy commit after the current `@`.
 * Equivalent to creating an empty git commit and then checking it out.
 */
class JjNewChangeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        object : Task.Backgroundable(project, "Creating New Change") {
            override fun run(indicator: ProgressIndicator) {
                repo.newChange()
                JjWorkingCopyCache.getInstance(project).refresh()
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "jj new Failed")
            }
        }.queue()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && findRepo(e) != null
    }

    private fun findRepo(e: AnActionEvent): JjRepository? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val manager = JjRepositoryManager.getInstance(project)
        return if (file != null) manager.getRepositoryForFile(file)
        else manager.getAll().firstOrNull()
    }
}
