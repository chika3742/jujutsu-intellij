package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager

/** Shows a list of local bookmarks and deletes the selected one via `jj bookmark delete`. */
class JjBookmarkDeleteAction : AnAction() {

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

        object : Task.Backgroundable(project, "Deleting Bookmark") {
            override fun run(indicator: ProgressIndicator) {
                repo.deleteBookmark(selected)
                JjChangeWatcher.getInstance(project).forceRefresh()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "Bookmark Deletion Failed")
            }
        }.queue()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && findRepo(e) != null
    }

    private fun findRepo(e: AnActionEvent): JjRepository? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val manager = JjRepositoryManager.getInstance(project)
        return if (file != null) manager.getRepositoryForFile(file)
        else manager.getAll().firstOrNull()
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
