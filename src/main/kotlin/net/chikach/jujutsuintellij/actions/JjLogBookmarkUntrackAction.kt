package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache

/** Stops tracking the tracked remote bookmark(s) on the commit(s) selected in the VCS Log. */
class JjLogBookmarkUntrackAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = trackedRemoteBookmarksAt(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = targets(e) ?: return

        val label = chooseBookmark(
            project,
            trackedRemoteBookmarksAt(e),
            JujutsuBundle.message("dialog.bookmark.untrack.title"),
        ) ?: return

        // Bookmark names may contain '@', so split on the last one (`name@remote`).
        val name = label.substringBeforeLast('@')
        val remote = label.substringAfterLast('@')

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.untrack.task"),
            JujutsuBundle.message("dialog.bookmark.untrack.error"),
        ) {
            target.repo.untrackBookmark(name, remote)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.untrack", label))
        }
    }

    /** Distinct `name@remote` labels of tracked remote bookmarks on the selected commit(s). */
    private fun trackedRemoteBookmarksAt(e: AnActionEvent): List<String> {
        val project = e.project ?: return emptyList()
        val target = targets(e) ?: return emptyList()
        val cache = JjCommitCache.getInstance(project)
        return target.hashes.flatMap { cache.get(it)?.trackedRemoteBookmarks.orEmpty() }.distinct()
    }
}
