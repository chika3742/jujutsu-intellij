package net.chikach.jujutsuintellij.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache

/**
 * Removes a bookmark locally on the commit selected in the VCS Log (`jj bookmark forget`).
 *
 * Local-only and undoable via `jj undo`, so it runs without a confirmation dialog and reports
 * completion with a notification. Use [JjLogBookmarkDeleteAction] to also delete it on the remote.
 */
class JjLogBookmarkForgetAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = bookmarksAt(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val name = chooseBookmark(project, bookmarksAt(e), JujutsuBundle.message("dialog.bookmark.forget.title"))
            ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.forget.task"),
            JujutsuBundle.message("dialog.bookmark.forget.error"),
        ) {
            target.repo.forgetBookmark(name)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Jujutsu")
                .createNotification(
                    JujutsuBundle.message("notification.bookmark.forget", name),
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