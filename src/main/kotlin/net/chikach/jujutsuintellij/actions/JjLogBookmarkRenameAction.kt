package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache

/** Renames a bookmark on the commit selected in the VCS Log (`jj bookmark rename`). */
class JjLogBookmarkRenameAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = bookmarksAt(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val oldName = chooseBookmark(project, bookmarksAt(e), JujutsuBundle.message("dialog.bookmark.rename.title"))
            ?: return
        val newName = Messages.showInputDialog(
            project,
            JujutsuBundle.message("dialog.bookmark.rename.message", oldName),
            JujutsuBundle.message("dialog.bookmark.rename.title"),
            null,
            oldName,
            null,
        )?.trim().orEmpty()
        if (newName.isEmpty() || newName == oldName) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.rename.task"),
            JujutsuBundle.message("dialog.bookmark.rename.error"),
        ) {
            target.repo.renameBookmark(oldName, newName)
        }
    }

    private fun bookmarksAt(e: AnActionEvent): List<String> {
        val project = e.project ?: return emptyList()
        val hash = target(e)?.hash ?: return emptyList()
        return JjCommitCache.getInstance(project).get(hash)?.bookmarks.orEmpty()
    }
}