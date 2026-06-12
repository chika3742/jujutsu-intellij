package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.log.VcsLogDataKeys
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache
import net.chikach.jujutsuintellij.ui.push.launchJjPushDialog

/**
 * Expands inline into one "Push '<bookmark>'" item per local bookmark on the commit selected in the
 * VCS Log. Hidden when the selection has no bookmarks.
 */
class JjLogBookmarkPushActionGroup : ActionGroup() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = bookmarksAtSelection(e).isNotEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) return EMPTY_ARRAY
        return bookmarksAtSelection(e).map { JjLogBookmarkPushAction(it) }.toTypedArray<AnAction>()
    }
}

/** Local bookmark names on the single commit selected in the VCS Log, from [JjCommitCache]. */
private fun bookmarksAtSelection(e: AnActionEvent): List<String> {
    val project = e.project ?: return emptyList()
    val commit = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.singleOrNull() ?: return emptyList()
    return JjCommitCache.getInstance(project).get(commit.hash.asString())?.bookmarks.orEmpty()
}

/**
 * Opens the push preview dialog for [bookmarkName] on the selected commit's repository — the same
 * [net.chikach.jujutsuintellij.ui.push.JjPushDialog] as the toolbar push, but scoped to this one
 * bookmark.
 */
class JjLogBookmarkPushAction(private val bookmarkName: String) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.BookmarkPush.item.text", bookmarkName)
        templatePresentation.description = JujutsuBundle.message("action.Jujutsu.Log.BookmarkPush.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return
        launchJjPushDialog(project, listOf(target.repo), bookmarkFilter = setOf(bookmarkName))
    }
}
