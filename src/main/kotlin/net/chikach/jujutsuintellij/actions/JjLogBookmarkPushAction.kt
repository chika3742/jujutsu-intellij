package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache

/** Pushes the bookmark(s) on the commit(s) selected in the VCS Log (`jj git push --bookmark`). */
class JjLogBookmarkPushAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = bookmarksAt(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = targets(e) ?: return

        val bookmarks = chooseBookmarks(project, bookmarksAt(e), JujutsuBundle.message("dialog.bookmark.push.title"))
            ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.push.task"),
            JujutsuBundle.message("dialog.bookmark.push.error"),
        ) {
            target.repo.gitPush(bookmarks = bookmarks)
        }
    }

    /** Distinct local bookmark names carried by the selected commit(s). */
    private fun bookmarksAt(e: AnActionEvent): List<String> {
        val project = e.project ?: return emptyList()
        val target = targets(e) ?: return emptyList()
        val cache = JjCommitCache.getInstance(project)
        return target.hashes.flatMap { cache.get(it)?.bookmarks.orEmpty() }.distinct()
    }
}