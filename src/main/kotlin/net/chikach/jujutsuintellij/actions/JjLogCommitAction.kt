package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.vcs.log.VcsLogDataKeys
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

/**
 * Base for VCS Log context-menu actions that operate on the single selected commit.
 *
 * Subclasses resolve the [Target] (repository + commit hash), optionally collect input on the EDT,
 * and run the `jj` mutation in [runInBackground]. The action is enabled only when exactly one commit
 * is selected in the log.
 */
abstract class JjLogCommitAction : AnAction() {

    protected data class Target(val repo: JjRepository, val hash: String)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = target(e) != null
    }

    protected fun target(e: AnActionEvent): Target? {
        val project = e.project ?: return null
        val commit = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.singleOrNull() ?: return null
        val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(commit.root)
        if (vcs?.name != JujutsuVcs.VCS_NAME) return null
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(commit.root)
        return Target(repo, commit.hash.asString())
    }

    /**
     * Runs [block] off the EDT, then refreshes the VCS Log, working-copy cache, and Local Changes via
     * [JjChangeWatcher.forceRefresh] (these operations rewrite the commit graph, so the log must be
     * refreshed explicitly).
     */
    protected fun runInBackground(project: Project, title: String, errorTitle: String, block: () -> Unit) {
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
}