package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.repo.JjRepository

/** Squashes the selected commit(s)' changes into a chosen destination (`jj squash --from … --into …`). */
class JjSquashAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        val target = targets(e)
        e.presentation.isEnabledAndVisible = target != null
        if (target != null) {
            e.presentation.text = JujutsuBundle.message(
                if (target.count > 1) "action.Jujutsu.Squash.text.multiple" else "action.Jujutsu.Squash.text"
            )
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = targets(e) ?: return

        // first_parent makes sense only for a single source; with several there is no common parent.
        val prefill = target.hashes.singleOrNull()?.let { JjRepository.firstParentRef(it) }
        val into = Messages.showInputDialog(
            project,
            JujutsuBundle.message("dialog.squash.message", target.count),
            JujutsuBundle.message("dialog.squash.title"),
            Messages.getQuestionIcon(),
            prefill,
            null,
        )?.trim().orEmpty()
        if (into.isEmpty()) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.squash.task"),
            JujutsuBundle.message("dialog.squash.error"),
        ) {
            target.repo.squash(from = target.revset, into = into)
        }
    }
}