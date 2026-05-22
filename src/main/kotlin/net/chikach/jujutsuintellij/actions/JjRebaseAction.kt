package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.repo.JjRepository.RebaseMode

/** Shared `jj rebase` flow; subclasses pick the revision-selection [mode] and prompt [messageKey]. */
abstract class JjRebaseAction(private val mode: RebaseMode, private val messageKey: String) : JjLogCommitAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val onto = Messages.showInputDialog(
            project,
            JujutsuBundle.message(messageKey, target.hash.take(8)),
            JujutsuBundle.message("dialog.rebase.title"),
            Messages.getQuestionIcon(),
        )?.trim().orEmpty()
        if (onto.isEmpty()) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.rebase.task"),
            JujutsuBundle.message("dialog.rebase.error"),
        ) {
            target.repo.rebase(revisions = target.hash, destination = onto, mode = mode)
        }
    }
}

/** Rebases only the selected commit (`jj rebase -r <rev> -o <dest>`). */
class JjRebaseRevisionAction : JjRebaseAction(RebaseMode.REVISION, "dialog.rebase.message.revision")

/** Rebases the selected commit and its descendants (`jj rebase -s <rev> -o <dest>`). */
class JjRebaseDescendantsAction : JjRebaseAction(RebaseMode.DESCENDANTS, "dialog.rebase.message.descendants")