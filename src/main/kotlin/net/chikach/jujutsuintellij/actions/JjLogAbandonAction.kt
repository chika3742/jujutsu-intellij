package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle

/** Abandons the commit(s) selected in the VCS Log (`jj abandon <revsets>`). */
class JjLogAbandonAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        val target = targets(e)
        e.presentation.isEnabledAndVisible = target != null
        if (target != null) {
            e.presentation.text = JujutsuBundle.message(
                if (target.count > 1) "action.Jujutsu.Log.Abandon.text.multiple" else "action.Jujutsu.Log.Abandon.text"
            )
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = targets(e) ?: return

        val confirmed = Messages.showYesNoDialog(
            project,
            JujutsuBundle.message("dialog.abandon.message", target.count),
            JujutsuBundle.message("dialog.abandon.title"),
            Messages.getWarningIcon(),
        )
        if (confirmed != Messages.YES) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.abandon.task"),
            JujutsuBundle.message("dialog.abandon.error"),
        ) {
            target.repo.abandon(target.revset)
        }
    }
}