package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepository.RebaseMode

/**
 * Shared `jj rebase` flow; subclasses pick the revision-selection [mode] and prompt [messageKey].
 * [multiSelect] enables operating on several selected commits at once (via a union revset); when
 * false the action targets exactly one commit.
 */
abstract class JjRebaseAction(
    private val mode: RebaseMode,
    private val messageKey: String,
    private val multiSelect: Boolean,
) : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = if (multiSelect) targets(e) != null else target(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val (repo, revisions, messageArg) = resolve(e) ?: return

        val onto = Messages.showInputDialog(
            project,
            JujutsuBundle.message(messageKey, messageArg),
            JujutsuBundle.message("dialog.rebase.title"),
            Messages.getQuestionIcon(),
        )?.trim().orEmpty()
        if (onto.isEmpty()) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.rebase.task"),
            JujutsuBundle.message("dialog.rebase.error"),
        ) {
            repo.rebase(revisions = revisions, destination = onto, mode = mode)
        }
    }

    /** Resolves (repo, revisions revset, message argument) for the current selection. */
    private fun resolve(e: AnActionEvent): Triple<JjRepository, String, Any>? {
        return if (multiSelect) {
            val t = targets(e) ?: return null
            Triple(t.repo, t.revset, t.count)
        } else {
            val t = target(e) ?: return null
            Triple(t.repo, t.hash, t.hash.take(8))
        }
    }
}

/** Rebases the selected commit(s) only (`jj rebase -r <revsets> -o <dest>`). */
class JjRebaseRevisionAction : JjRebaseAction(RebaseMode.REVISION, "dialog.rebase.message.revision", multiSelect = true) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        targets(e)?.let {
            e.presentation.text = JujutsuBundle.message(
                if (it.count > 1) "action.Jujutsu.RebaseRevision.text.multiple" else "action.Jujutsu.RebaseRevision.text"
            )
        }
    }
}

/** Rebases the selected commit and its descendants (`jj rebase -s <rev> -o <dest>`). */
class JjRebaseDescendantsAction : JjRebaseAction(RebaseMode.DESCENDANTS, "dialog.rebase.message.descendants", multiSelect = false)