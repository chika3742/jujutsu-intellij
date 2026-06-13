package net.chikach.jujutsuintellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.actions.notifyJjInfo
import net.chikach.jujutsuintellij.actions.runJjInBackground
import net.chikach.jujutsuintellij.caches.JjBookmarkCache
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjOperationException
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.model.JjCommitRef
import net.chikach.jujutsuintellij.ui.push.launchJjPushDialog

private const val ALLOW_BACKWARDS_HINT = "--allow-backwards"

/**
 * Builds the action group for the toolbar revision-widget popup: a Fetch row, a "Push" row (opens
 * the [net.chikach.jujutsuintellij.ui.push.JjPushDialog] preview), one submenu per local bookmark
 * (Move to working copy / Push / Rename / Track or Untrack per remote / Remove locally / Delete),
 * and a "Remote bookmarks" section listing untracked remote bookmarks that have no local
 * counterpart, each offering Track.
 */
fun buildJjToolbarPopupGroup(project: Project): DefaultActionGroup = DefaultActionGroup().apply {
    add(ActionManager.getInstance().getAction("Jujutsu.Fetch"))
    add(PushAction())
    addSeparator(JujutsuBundle.message("popup.bookmarks.separator"))

    val remoteOnly = mutableListOf<Pair<JjRepository, JjCommitRef>>()
    for (entry in JjBookmarkCache.getInstance(project).entries) {
        val (locals, remotes) = entry.refs.partition { it.isLocal }
        val remotesByName = remotes.groupBy { it.name }
        val localNames = locals.mapTo(mutableSetOf()) { it.name }

        for (local in locals) {
            add(bookmarkSubmenu(entry.repo, local.name, remotesByName[local.name].orEmpty()))
        }
        for (remote in remotes) {
            // Untracked remote bookmark with no local counterpart (e.g. a colleague's branch).
            if (!remote.tracked && remote.name !in localNames) remoteOnly += entry.repo to remote
        }
    }

    if (remoteOnly.isNotEmpty()) {
        addSeparator(JujutsuBundle.message("popup.remoteBookmarks.separator"))
        for ((repo, ref) in remoteOnly) {
            add(TrackBookmarkAction(repo, ref.name, ref.remote!!))
        }
    }
}

private fun bookmarkSubmenu(repo: JjRepository, name: String, remotes: List<JjCommitRef>): DefaultActionGroup =
    DefaultActionGroup(name, true).apply {
        templatePresentation.icon = AllIcons.Vcs.Branch
        add(MoveToWorkingCopyAction(repo, name))
        add(PushBookmarkAction(repo, name))
        add(RenameBookmarkAction(repo, name))
        if (remotes.isNotEmpty()) {
            addSeparator()
            for (remote in remotes) {
                val remoteName = remote.remote!!
                if (remote.tracked) {
                    add(UntrackBookmarkAction(repo, name, remoteName))
                } else {
                    add(TrackBookmarkAction(repo, name, remoteName))
                }
            }
        }
        addSeparator()
        add(RemoveLocallyAction(repo, name))
        add(DeleteBookmarkAction(repo, name))
    }

/** Base for the popup's inline actions; all are always enabled, so update runs off the EDT. */
private abstract class PopupAction(text: String) : AnAction(text) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class PushAction :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.Push.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        launchJjPushDialog(project, JjRepositoryManager.getInstance(project).getAll().toList())
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
                @Suppress("DialogTitleCapitalization")
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
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.push.success", name))
        }
    }
}

private class TrackBookmarkAction(
    private val repo: JjRepository,
    private val name: String,
    private val remote: String,
) : PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.TrackBookmark.text", "$name@$remote")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("dialog.bookmark.track.task"), JujutsuBundle.message("dialog.bookmark.track.error")) {
            repo.trackBookmark(name, remote)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.track", "$name@$remote"))
        }
    }
}

private class UntrackBookmarkAction(
    private val repo: JjRepository,
    private val name: String,
    private val remote: String,
) : PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.UntrackBookmark.text", "$name@$remote")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("dialog.bookmark.untrack.task"), JujutsuBundle.message("dialog.bookmark.untrack.error")) {
            repo.untrackBookmark(name, remote)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.untrack", "$name@$remote"))
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
    PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.ForgetBookmark.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("dialog.bookmark.forget.task"), JujutsuBundle.message("dialog.bookmark.forget.error")) {
            repo.forgetBookmark(name)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.forget", name))
        }
    }
}

private class DeleteBookmarkAction(private val repo: JjRepository, private val name: String) :
    PopupAction(JujutsuBundle.message("action.Jujutsu.Popup.DeleteBookmark.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runJjOp(project, JujutsuBundle.message("dialog.bookmark.delete.task"), JujutsuBundle.message("dialog.bookmark.delete.error")) {
            repo.deleteBookmark(name)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.delete", name))
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
