package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.repo.JjRepository

/** Shows a list of local bookmarks and deletes the selected one via `jj bookmark delete`. */
class JjBookmarkDeleteAction : JjRepositoryAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        val bookmarks = loadBookmarks(project, repo)
        if (bookmarks.isEmpty()) {
            Messages.showInfoMessage(project, "No local bookmarks found.", "Delete Bookmark")
            return
        }

        val dialog = BookmarkSelectDialog(project, bookmarks, "Select Bookmark to Delete")
        if (!dialog.showAndGet()) return
        val selected = dialog.selectedBookmark ?: return

        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete bookmark \"$selected\"?",
            "Delete Bookmark",
            Messages.getWarningIcon(),
        )
        if (confirmed != Messages.YES) return

        runInBackground(project, "Deleting Bookmark", "Bookmark Deletion Failed") {
            repo.deleteBookmark(selected)
        }
    }

    private fun loadBookmarks(project: Project, repo: JjRepository): List<String> {
        var names = emptyList<String>()
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                names = repo.listBookmarks(revset = "bookmarks()")
                    .map { it.name }
                    .sorted()
            },
            "Loading Bookmarks",
            false,
            project,
        )
        return names
    }
}
