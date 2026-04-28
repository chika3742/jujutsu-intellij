package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ListSelectionModel

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

        val dialog = BookmarkSelectDialog(project, bookmarks)
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
                val result = JjCommands.getInstance().bookmarkDelete(repo, selected)
                if (!result.isSuccess) {
                    throw RuntimeException(result.stderr.trim().ifEmpty { "jj bookmark delete failed (exit ${result.exitCode})" })
                }
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
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
                val result = JjCommands.getInstance().bookmarkList(repo)
                if (result.isSuccess) {
                    names = parseLocalBookmarkNames(result.stdout)
                }
            },
            "Loading Bookmarks",
            false,
            project,
        )
        return names
    }

    private fun parseLocalBookmarkNames(output: String): List<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val colonIdx = line.indexOf(':')
                if (colonIdx < 0) return@mapNotNull null
                val token = line.substring(0, colonIdx)
                // Skip remote tracking entries like "name@origin"
                if ('@' in token) null else token
            }
            .distinct()
            .sorted()
            .toList()

    private class BookmarkSelectDialog(project: Project, bookmarks: List<String>) : DialogWrapper(project) {
        private val list = JBList(bookmarks).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0
        }

        val selectedBookmark: String? get() = list.selectedValue

        init {
            title = "Select Bookmark to Delete"
            init()
        }

        override fun createCenterPanel(): JComponent =
            JBScrollPane(list).apply {
                preferredSize = Dimension(300, 200)
            }
    }
}
