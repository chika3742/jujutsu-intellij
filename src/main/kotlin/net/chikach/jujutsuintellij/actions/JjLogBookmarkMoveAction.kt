package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjOperationException
import net.chikach.jujutsuintellij.repo.JjRepository

/** Moves an existing local bookmark onto the commit selected in the VCS Log (`jj bookmark set`). */
class JjLogBookmarkMoveAction : JjLogCommitAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val bookmarks = loadLocalBookmarks(project, target.repo)
        if (bookmarks.isEmpty()) {
            Messages.showInfoMessage(
                project,
                JujutsuBundle.message("dialog.bookmark.move.empty"),
                JujutsuBundle.message("dialog.bookmark.move.title"),
            )
            return
        }

        val dialog = BookmarkSelectDialog(project, bookmarks, JujutsuBundle.message("dialog.bookmark.move.title"))
        if (!dialog.showAndGet()) return
        val name = dialog.selectedBookmark ?: return

        object : Task.Backgroundable(project, JujutsuBundle.message("dialog.bookmark.move.task")) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    target.repo.setBookmark(name, target.hash)
                } catch (ex: JjOperationException) {
                    // jj refuses backwards/sideways moves unless --allow-backwards is given.
                    if (!ex.stderr.contains(ALLOW_BACKWARDS_HINT)) throw ex
                    if (!confirmAllowBackwards(project, name)) return
                    target.repo.setBookmark(name, target.hash, allowBackwards = true)
                }
                JjChangeWatcher.getInstance(project).forceRefresh()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, JujutsuBundle.message("dialog.bookmark.move.error"))
            }
        }.queue()
    }

    private fun confirmAllowBackwards(project: Project, name: String): Boolean {
        var allow = false
        ApplicationManager.getApplication().invokeAndWait {
            allow = Messages.showYesNoDialog(
                project,
                JujutsuBundle.message("dialog.bookmark.move.backwards.message", name),
                JujutsuBundle.message("dialog.bookmark.move.backwards.title"),
                Messages.getWarningIcon(),
            ) == Messages.YES
        }
        return allow
    }

    /** Local bookmark names ordered by their commit's committer date, most recent first. */
    private fun loadLocalBookmarks(project: Project, repo: JjRepository): List<String> {
        var names = emptyList<String>()
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                names = repo.listBookmarks(revset = "bookmarks()", sortByCommitterDate = true)
                    .filter { it.isLocal }
                    .map { it.name }
                    .distinct()
            },
            JujutsuBundle.message("dialog.bookmark.loading"),
            false,
            project,
        )
        return names
    }

    private companion object {
        const val ALLOW_BACKWARDS_HINT = "--allow-backwards"
    }
}