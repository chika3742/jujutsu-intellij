package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.repo.JjRepository

/** Squashes the selected commit's changes into a chosen destination (`jj squash --from … --into …`). */
class JjSquashAction : JjLogCommitAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val into = Messages.showInputDialog(
            project,
            JujutsuBundle.message("dialog.squash.message", target.hash.take(8)),
            JujutsuBundle.message("dialog.squash.title"),
            Messages.getQuestionIcon(),
            JjRepository.firstParentRef(target.hash),
            null,
        )?.trim().orEmpty()
        if (into.isEmpty()) return

        runInBackground(
            project,
            target.repo.root,
            JujutsuBundle.message("dialog.squash.task"),
            JujutsuBundle.message("dialog.squash.error"),
        ) {
            target.repo.squash(from = target.hash, into = into)
        }
    }
}