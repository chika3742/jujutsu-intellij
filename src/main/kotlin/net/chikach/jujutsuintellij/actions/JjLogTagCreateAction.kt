package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle

/**
 * Creates a tag on the commit selected in the VCS Log (`jj tag set -r <rev>`). The dialog's
 * checkbox adds `--allow-move`, which jj requires to retarget an existing tag, so a separate
 * "move tag" action is unnecessary.
 */
class JjLogTagCreateAction : JjLogCommitAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val result = Messages.showInputDialogWithCheckBox(
            JujutsuBundle.message("dialog.tag.create.message", target.hash.take(8)),
            JujutsuBundle.message("dialog.tag.create.title"),
            JujutsuBundle.message("dialog.tag.create.allowMove"),
            false,
            true,
            null,
            null,
            null,
        )
        val name = result.first?.trim().orEmpty()
        if (name.isEmpty()) return
        val allowMove = result.second == true

        runInBackground(
            project,
            JujutsuBundle.message("dialog.tag.create.task"),
            JujutsuBundle.message("dialog.tag.create.error"),
        ) {
            target.repo.setTag(name, target.hash, allowMove)
        }
    }
}
