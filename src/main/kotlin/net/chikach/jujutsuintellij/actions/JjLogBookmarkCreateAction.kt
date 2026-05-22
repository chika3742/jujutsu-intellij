package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle

/** Creates a new bookmark on the commit selected in the VCS Log (`jj bookmark create -r <rev>`). */
class JjLogBookmarkCreateAction : JjLogCommitAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val name = Messages.showInputDialog(
            project,
            JujutsuBundle.message("dialog.bookmark.create.message", target.hash.take(8)),
            JujutsuBundle.message("dialog.bookmark.create.title"),
            null,
        )?.trim().orEmpty()
        if (name.isEmpty()) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.create.task"),
            JujutsuBundle.message("dialog.bookmark.create.error"),
        ) {
            target.repo.createBookmark(name, target.hash)
        }
    }
}