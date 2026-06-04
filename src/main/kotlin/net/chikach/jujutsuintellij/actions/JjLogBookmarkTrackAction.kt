package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache

/** Starts tracking the untracked remote bookmark(s) on the commit(s) selected in the VCS Log. */
class JjLogBookmarkTrackAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = untrackedRemoteBookmarksAt(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = targets(e) ?: return

        val label = chooseBookmark(
            project,
            untrackedRemoteBookmarksAt(e),
            JujutsuBundle.message("dialog.bookmark.track.title"),
        ) ?: return

        // Bookmark names may contain '@', so split on the last one (`name@remote`).
        val name = label.substringBeforeLast('@')
        val remote = label.substringAfterLast('@')

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.track.task"),
            JujutsuBundle.message("dialog.bookmark.track.error"),
        ) {
            target.repo.trackBookmark(name, remote)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.track", label))
        }
    }

    /** Distinct `name@remote` labels of untracked remote bookmarks on the selected commit(s). */
    private fun untrackedRemoteBookmarksAt(e: AnActionEvent): List<String> {
        val project = e.project ?: return emptyList()
        val target = targets(e) ?: return emptyList()
        val cache = JjCommitCache.getInstance(project)
        return target.hashes.flatMap { cache.get(it)?.untrackedRemoteBookmarks.orEmpty() }.distinct()
    }
}
