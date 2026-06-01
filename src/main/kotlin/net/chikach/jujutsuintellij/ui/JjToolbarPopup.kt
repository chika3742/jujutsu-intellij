package net.chikach.jujutsuintellij.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.actions.runJjInBackground
import net.chikach.jujutsuintellij.caches.JjBookmarkCache
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjOperationException
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import com.intellij.openapi.actionSystem.DefaultActionGroup

private const val ALLOW_BACKWARDS_HINT = "--allow-backwards"

/**
 * Builds the action group for the toolbar revision-widget popup: a Fetch row, a "Push nearest
 * bookmark" row (plain `jj git push`, whose default pushes the nearest tracked bookmark), and one
 * submenu per local bookmark (Move to working copy / Push / Rename / Remove locally / Delete).
 */
fun buildJjToolbarPopupGroup(project: Project): DefaultActionGroup = DefaultActionGroup().apply {
    add(ActionManager.getInstance().getAction("Jujutsu.Fetch"))
    add(PushNearestAction())
    addSeparator(JujutsuBundle.message("popup.bookmarks.separator"))
    for (entry in JjBookmarkCache.getInstance(project).entries) {
        add(bookmarkSubmenu(entry.repo, entry.name))
    }
}

private fun bookmarkSubmenu(repo: JjRepository, name: String): DefaultActionGroup =
    DefaultActionGroup(name, true).apply {
        templatePresentation.icon = AllIcons.Vcs.Branch
        add(MoveToWorkingCopyAction(repo, name))
        add(PushBookmarkAction(repo, name))
        add(RenameBookmarkAction(repo, name))
        addSeparator()
        add(RemoveLocallyAction(repo, name))
        add(DeleteBookmarkAction(repo, name))
    }

/** Base for the popup's inline actions; all are always enabled, so update runs off the EDT. */
private abstract class PopupAction(text: String) : AnAction(text) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class PushNearestAction :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.PushNearest.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("action.Jujutsu.Popup.PushNearest.text"), "jj git push Failed") {
            JjRepositoryManager.getInstance(project).getAll().forEach { it.gitPush() }
        }
    }
}

private class MoveToWorkingCopyAction(private val repo: JjRepository, private val name: String) :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.MoveToWorkingCopy.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, JujutsuBundle.message("dialog.bookmark.move.task")) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    repo.setBookmark(name, "@")
                } catch (ex: JjOperationException) {
                    if (!ex.stderr.contains(ALLOW_BACKWARDS_HINT)) throw ex
                    if (!confirmAllowBackwards(project, name)) return
                    repo.setBookmark(name, "@", allowBackwards = true)
                }
                JjChangeWatcher.getInstance(project).forceRefresh()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, JujutsuBundle.message("dialog.bookmark.move.error"))
            }
        }.queue()
    }
}

private class PushBookmarkAction(private val repo: JjRepository, private val name: String) :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.PushBookmark.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("dialog.bookmark.push.task"), JujutsuBundle.message("dialog.bookmark.push.error")) {
            repo.gitPush(bookmarks = listOf(name))
        }
    }
}

private class RenameBookmarkAction(private val repo: JjRepository, private val name: String) :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.RenameBookmark.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val newName = Messages.showInputDialog(
            project,
            JujutsuBundle.message("dialog.bookmark.rename.message", name),
            JujutsuBundle.message("dialog.bookmark.rename.title"),
            null,
            name,
            null,
        )?.trim().orEmpty()
        if (newName.isEmpty() || newName == name) return

        runJjOp(project, JujutsuBundle.message("dialog.bookmark.rename.task"), JujutsuBundle.message("dialog.bookmark.rename.error")) {
            repo.renameBookmark(name, newName)
        }
    }
}

private class RemoveLocallyAction(private val repo: JjRepository, private val name: String) :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Log.BookmarkForget.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("dialog.bookmark.forget.task"), JujutsuBundle.message("dialog.bookmark.forget.error")) {
            repo.forgetBookmark(name)
            notify(project, JujutsuBundle.message("notification.bookmark.forget", name))
        }
    }
}

private class DeleteBookmarkAction(private val repo: JjRepository, private val name: String) :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Log.BookmarkDelete.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("dialog.bookmark.delete.task"), JujutsuBundle.message("dialog.bookmark.delete.error")) {
            repo.deleteBookmark(name)
            notify(project, JujutsuBundle.message("notification.bookmark.delete", name))
        }
    }
}

/** @see net.chikach.jujutsuintellij.actions.runJjInBackground */
private fun runJjOp(project: Project, title: String, errorTitle: String, block: () -> Unit) =
    runJjInBackground(project, title, errorTitle, block)

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

private fun notify(project: Project, content: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Jujutsu")
        .createNotification(content, NotificationType.INFORMATION)
        .notify(project)
}