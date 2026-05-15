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

/** Creates a new bookmark at `@` via `jj bookmark create <name>`. */
class JjBookmarkCreateAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        val name = Messages.showInputDialog(
            project,
            "Bookmark name:",
            "Create Bookmark",
            null,
        )?.trim() ?: return
        if (name.isEmpty()) return

        object : Task.Backgroundable(project, "Creating Bookmark") {
            override fun run(indicator: ProgressIndicator) {
                repo.createBookmark(name)
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "Bookmark Creation Failed")
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
