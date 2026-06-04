package net.chikach.jujutsuintellij.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager

/**
 * Base for actions that operate on the Jujutsu repository resolved from the current file context
 * (toolbar, main menu, project view) rather than a VCS Log selection.
 *
 * Provides repository resolution ([findRepo]), a default enablement rule, and a background-task
 * helper ([runInBackground]). Log-selection actions use the sibling base [JjLogCommitAction]; both
 * share [runJjInBackground] for the off-EDT run + refresh + error-dialog pattern.
 */
abstract class JjRepositoryAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** Enabled when a project is open and a Jujutsu repository resolves. Override for richer rules. */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && findRepo(e) != null
    }

    /**
     * The repository for the file in context ([CommonDataKeys.VIRTUAL_FILE]), falling back to the
     * first registered repository when no file is in context. `null` when the project has none.
     */
    protected fun findRepo(e: AnActionEvent): JjRepository? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val manager = JjRepositoryManager.getInstance(project)
        return if (file != null) manager.getRepositoryForFile(file)
        else manager.getAll().firstOrNull()
    }

    /** @see runJjInBackground */
    protected fun runInBackground(project: Project, title: String, errorTitle: String, block: () -> Unit) =
        runJjInBackground(project, title, errorTitle, block)
}

/** Runs [block] off the EDT, then refreshes via [JjChangeWatcher]; shows [errorTitle] on failure. */
internal fun runJjInBackground(project: Project, title: String, errorTitle: String, block: () -> Unit) {
    object : Task.Backgroundable(project, title) {
        override fun run(indicator: ProgressIndicator) {
            block()
            JjChangeWatcher.getInstance(project).forceRefresh()
        }

        override fun onThrowable(error: Throwable) {
            Messages.showErrorDialog(project, error.message, errorTitle)
        }
    }.queue()
}

/** Shows an informational notification in the "Jujutsu" notification group. */
internal fun notifyJjInfo(project: Project, content: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Jujutsu")
        .createNotification(content, NotificationType.INFORMATION)
        .notify(project)
}
