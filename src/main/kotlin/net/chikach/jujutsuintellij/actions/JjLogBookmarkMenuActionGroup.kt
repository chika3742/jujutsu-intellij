package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.vcs.log.VcsLogDataKeys
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache
import net.chikach.jujutsuintellij.repo.JjRepository

/**
 * Top-level dynamic group in the VCS Log context menu that expands into one `Bookmark '<label>'`
 * submenu per bookmark on the selected commit:
 *
 * - each local bookmark → `Bookmark '<name>'` with Push / Rename / Delete / Remove Locally,
 *   an `Untrack '<name>@<remote>'` item per tracked remote, and Create Remote Bookmark;
 * - each untracked remote bookmark → `Bookmark '<name>@<remote>'` with Track, plus Delete.
 *
 * Tracked remote bookmarks get no top-level submenu of their own; they are managed (Untrack) from
 * their local counterpart's submenu. Untracked remote bookmarks always carry their `@<remote>`
 * suffix so they stay distinct from local ones. Hidden when the selection has no bookmarks (or when
 * multiple commits are selected, since the per-bookmark operations target exactly one commit).
 */
class JjLogBookmarkMenuActionGroup : ActionGroup() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val info = infoAtSelection(e)
        e.presentation.isEnabledAndVisible = info != null && (
            info.bookmarks.isNotEmpty() || info.untrackedRemoteBookmarks.isNotEmpty()
            )
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val info = e?.let { infoAtSelection(it) } ?: return EMPTY_ARRAY
        val children = mutableListOf<AnAction>()

        for (name in info.bookmarks) {
            val trackedRemotes = info.trackedRemoteBookmarks
                .filter { it.substringBeforeLast('@') == name }
                .map { it.substringAfterLast('@') }
            children += LocalBookmarkActionGroup(name, trackedRemotes)
        }
        for (label in info.untrackedRemoteBookmarks) {
            children += RemoteBookmarkActionGroup(label.substringBeforeLast('@'), label.substringAfterLast('@'))
        }

        return children.toTypedArray()
    }
}

/**
 * `Bookmark '<name>'` submenu for a local bookmark: Push, Rename, Delete, Remove Locally, an
 * `Untrack '<name>@<remote>'` item per tracked remote, and Create Remote Bookmark. Tracked remotes
 * are managed here rather than as their own top-level submenus.
 */
private class LocalBookmarkActionGroup(
    private val name: String,
    private val trackedRemotes: List<String>,
) : ActionGroup(JujutsuBundle.message("action.Jujutsu.Log.Bookmark.submenu", name), true) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val children = mutableListOf<AnAction>(
            JjLogBookmarkPushAction(name),
            JjBoundBookmarkRenameAction(name),
            JjBoundBookmarkDeleteAction(name),
            JjBoundBookmarkForgetAction(name),
            Separator.getInstance(),
        )
        for (remote in trackedRemotes) {
            children += JjBoundBookmarkUntrackAction(name, remote)
        }
        children += JjBookmarkCreateRemoteAction(name)
        return children.toTypedArray()
    }
}

/**
 * `Bookmark '<name>@<remote>'` submenu for an untracked remote bookmark: Track, plus Delete.
 */
private class RemoteBookmarkActionGroup(
    private val name: String,
    private val remote: String,
) : ActionGroup(JujutsuBundle.message("action.Jujutsu.Log.Bookmark.submenu", "$name@$remote"), true) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
        JjBoundBookmarkTrackAction(name, remote),
        JjBoundRemoteBookmarkDeleteAction(name, remote),
    )
}

/** Renames the bound local bookmark (`jj bookmark rename`). */
private class JjBoundBookmarkRenameAction(private val name: String) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.rename")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val newName = Messages.showInputDialog(
            project,
            JujutsuBundle.message("dialog.bookmark.rename.message", name),
            JujutsuBundle.message("dialog.bookmark.rename.title"),
            null,
            name,
            null,
        )?.trim().orEmpty()
        if (newName.isEmpty() || newName == name) return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.rename.task"),
            JujutsuBundle.message("dialog.bookmark.rename.error"),
        ) {
            target.repo.renameBookmark(name, newName)
        }
    }
}

/** Deletes the bound local bookmark, recording the deletion for the next push (`jj bookmark delete`). */
private class JjBoundBookmarkDeleteAction(private val name: String) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.delete")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.delete.task"),
            JujutsuBundle.message("dialog.bookmark.delete.error"),
        ) {
            target.repo.deleteBookmark(name)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.delete", name))
        }
    }
}

/** Removes the bound local bookmark locally without recording a deletion (`jj bookmark forget`). */
private class JjBoundBookmarkForgetAction(private val name: String) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.forget")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.forget.task"),
            JujutsuBundle.message("dialog.bookmark.forget.error"),
        ) {
            target.repo.forgetBookmark(name)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.forget", name))
        }
    }
}

/** Stops tracking a remote bookmark of the bound local bookmark (`jj bookmark untrack`). */
private class JjBoundBookmarkUntrackAction(
    private val name: String,
    private val remote: String,
) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.untrack", "$name@$remote")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.untrack.task"),
            JujutsuBundle.message("dialog.bookmark.untrack.error"),
        ) {
            target.repo.untrackBookmark(name, remote)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.untrack", "$name@$remote"))
        }
    }
}

/** Starts tracking the bound untracked remote bookmark (`jj bookmark track`). */
private class JjBoundBookmarkTrackAction(
    private val name: String,
    private val remote: String,
) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.track")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.track.task"),
            JujutsuBundle.message("dialog.bookmark.track.error"),
        ) {
            target.repo.trackBookmark(name, remote)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.track", "$name@$remote"))
        }
    }
}

/**
 * Deletes an untracked remote bookmark and propagates the deletion on the next push
 * (`jj bookmark delete`). `jj bookmark delete` only acts on local bookmarks, so the untracked
 * remote bookmark must be tracked first to give the deletion a local bookmark to propagate from.
 */
private class JjBoundRemoteBookmarkDeleteAction(
    private val name: String,
    private val remote: String,
) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.delete")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.delete.task"),
            JujutsuBundle.message("dialog.bookmark.delete.error"),
        ) {
            target.repo.trackBookmark(name, remote)
            target.repo.deleteBookmark(name)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.delete", "$name@$remote"))
        }
    }
}

/**
 * Creates a remote bookmark of the same name on a chosen remote, locally and without pushing:
 * `jj bookmark track <name> --remote=<remote>` records an `@<remote> (not created yet)` tracked
 * remote bookmark. Offered only for remotes that do not already have a bookmark of this name.
 */
private class JjBookmarkCreateRemoteAction(private val name: String) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.createRemote")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        val remotes = loadRemotes(project) {
            val existing = target.repo.listBookmarks(allRemotes = true)
                .filter { !it.isLocal && it.name == name }
                .mapNotNull { it.remote }
                .toSet()
            target.repo.listGitRemotes()
                .filter { it != JjRepository.INTERNAL_GIT_REMOTE && it !in existing }
        }
        if (remotes.isEmpty()) {
            Messages.showInfoMessage(
                project,
                JujutsuBundle.message("dialog.bookmark.createRemote.empty"),
                JujutsuBundle.message("dialog.bookmark.createRemote.title"),
            )
            return
        }
        val remote = chooseBookmark(project, remotes, JujutsuBundle.message("dialog.bookmark.createRemote.title"))
            ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.bookmark.createRemote.task"),
            JujutsuBundle.message("dialog.bookmark.createRemote.error"),
        ) {
            target.repo.trackBookmark(name, remote)
            notifyJjInfo(project, JujutsuBundle.message("notification.bookmark.createRemote", "$name@$remote"))
        }
    }
}

/** The cached ref info of the single commit selected in the VCS Log, or null unless exactly one is selected. */
private fun infoAtSelection(e: AnActionEvent): JjCommitCache.Info? {
    val project = e.project ?: return null
    val commit = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.singleOrNull() ?: return null
    return JjCommitCache.getInstance(project).get(commit.hash.asString())
}

/** Runs [compute] under a modal progress indicator (jj queries must not run on the EDT). */
private fun loadRemotes(project: Project, compute: () -> List<String>): List<String> {
    var remotes = emptyList<String>()
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
        { remotes = compute() },
        JujutsuBundle.message("dialog.bookmark.loading"),
        false,
        project,
    )
    return remotes
}
