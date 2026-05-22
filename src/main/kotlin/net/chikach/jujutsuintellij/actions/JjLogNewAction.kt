package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle

/**
 * Creates a new change on top of the commit(s) selected in the VCS Log (`jj new <revsets>`).
 *
 * With multiple commits selected, the new change becomes a merge with all of them as parents.
 */
class JjLogNewAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        val target = targets(e)
        e.presentation.isEnabledAndVisible = target != null
        if (target != null) {
            e.presentation.text = JujutsuBundle.message(
                if (target.count > 1) "action.Jujutsu.Log.New.text.multiple" else "action.Jujutsu.Log.New.text"
            )
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = targets(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.new.task"),
            JujutsuBundle.message("dialog.new.error"),
        ) {
            target.repo.newChange(target.revset)
        }
    }
}