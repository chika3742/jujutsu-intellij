package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle

/** Abandons the commit selected in the VCS Log (`jj abandon <rev>`). */
class JjLogAbandonAction : JjLogCommitAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val confirmed = Messages.showYesNoDialog(
            project,
            JujutsuBundle.message("dialog.abandon.message", target.hash.take(8)),
            JujutsuBundle.message("dialog.abandon.title"),
            Messages.getWarningIcon(),
        )
        if (confirmed != Messages.YES) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.abandon.task"),
            JujutsuBundle.message("dialog.abandon.error"),
        ) {
            target.repo.abandon(target.hash)
        }
    }
}