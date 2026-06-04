package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjOperationException
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

/** Runs `jj git push` for all Jujutsu repositories in the project. */
class JjPushAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        object : Task.Backgroundable(project, "Pushing to Remote") {
            override fun run(indicator: ProgressIndicator) {
                val repos = JjRepositoryManager.getInstance(project).getAll()
                val errors = mutableListOf<String>()

                for (repo in repos) {
                    indicator.text = "Pushing…"
                    try {
                        repo.gitPush()
                    } catch (e: JjOperationException) {
                        errors += e.message ?: "jj git push failed"
                    }
                }

                if (errors.isNotEmpty()) {
                    throw RuntimeException(errors.joinToString("\n"))
                }
                JjChangeWatcher.getInstance(project).forceRefresh()
                notifyJjInfo(project, JujutsuBundle.message("notification.push"))
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "jj git push Failed")
            }
        }.queue()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { JujutsuVcs.isActiveIn(it) } == true
    }
}
