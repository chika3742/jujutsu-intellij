package net.chikach.jujutsuintellij.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache

/**
 * Deletes a bookmark on the commit selected in the VCS Log (`jj bookmark delete`).
 *
 * Unlike [JjLogBookmarkForgetAction], the deletion is recorded and propagates to tracked remotes on
 * the next push. Undoable via `jj undo`, so it runs without a confirmation dialog and reports
 * completion with a notification.
 */
class JjLogBookmarkDeleteAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = bookmarksAt(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val name = chooseBookmark(project, bookmarksAt(e), JujutsuBundle.message("dialog.bookmark.delete.title"))
            ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.delete.task"),
            JujutsuBundle.message("dialog.bookmark.delete.error"),
        ) {
            target.repo.deleteBookmark(name)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Jujutsu")
                .createNotification(
                    JujutsuBundle.message("notification.bookmark.delete", name),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
    }

    private fun bookmarksAt(e: AnActionEvent): List<String> {
        val project = e.project ?: return emptyList()
        val hash = target(e)?.hash ?: return emptyList()
        return JjCommitCache.getInstance(project).get(hash)?.bookmarks.orEmpty()
    }
}