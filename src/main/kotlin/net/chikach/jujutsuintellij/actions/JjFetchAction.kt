package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

/** Runs `jj git fetch` for all Jujutsu repositories in the project. */
class JjFetchAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        object : Task.Backgroundable(project, "Fetching from Remote") {
            override fun run(indicator: ProgressIndicator) {
                val repos = JjRepositoryManager.getInstance(project).getAll()
                val commands = JjCommands.getInstance()
                val errors = mutableListOf<String>()

                for (repo in repos) {
                    indicator.text = "Fetching…"
                    val result = commands.gitFetch(repo)
                    if (!result.isSuccess) {
                        errors += result.stderr.trim().ifEmpty { "exit ${result.exitCode}" }
                    }
                }

                if (errors.isNotEmpty()) {
                    throw RuntimeException(errors.joinToString("\n"))
                }
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "jj git fetch Failed")
            }
        }.queue()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { JujutsuVcs.isActiveIn(it) } == true
    }
}
