package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import net.chikach.jujutsuintellij.repo.JjRepository

/**
 * Opens a dialog pre-filled with the current description of `@` and saves it via `jj describe -m`.
 */
class JjDescribeAction : JjRepositoryAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        val current = loadCurrentDescription(project, repo)
        val dialog = DescribeDialog(project, current, "Describe Working Copy")
        if (!dialog.showAndGet()) return

        val newMessage = dialog.message
        if (newMessage.isBlank()) return

        runInBackground(project, "Describing Working Copy", "jj describe Failed") {
            repo.describe(newMessage)
        }
    }

    private fun loadCurrentDescription(project: Project, repo: JjRepository): String {
        var description = ""
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            { description = repo.workingCopyDescription() },
            "Loading Current Description",
            false,
            project,
        )
        return description
    }
}
