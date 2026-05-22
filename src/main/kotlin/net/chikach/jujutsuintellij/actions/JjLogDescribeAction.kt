package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache

/** Edits the description of the commit selected in the VCS Log (`jj describe <rev> -m …`). */
class JjLogDescribeAction : JjLogCommitAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val current = JjCommitCache.getInstance(project).get(target.hash)?.description?.trimEnd('\n').orEmpty()
        val dialog = DescribeDialog(
            project, current,
            JujutsuBundle.message("dialog.describe.title", target.hash.take(8)),
        )
        if (!dialog.showAndGet()) return

        val message = dialog.message
        if (message.isBlank()) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.describe.task"),
            JujutsuBundle.message("dialog.describe.error"),
        ) {
            target.repo.describe(message, target.hash)
        }
    }
}