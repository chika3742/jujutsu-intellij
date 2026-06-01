package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Abandons the current working-copy commit (`@`) via `jj abandon @`.
 * Requires confirmation before executing since this is irreversible without `jj undo`.
 */
class JjAbandonAction : JjRepositoryAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        val confirmed = Messages.showYesNoDialog(
            project,
            "Abandon the current working-copy change (@)? This can be undone with `jj undo`.",
            "Abandon Change",
            Messages.getWarningIcon(),
        )
        if (confirmed != Messages.YES) return

        runInBackground(project, "Abandoning Change", "jj abandon Failed") {
            repo.abandon()
        }
    }
}
