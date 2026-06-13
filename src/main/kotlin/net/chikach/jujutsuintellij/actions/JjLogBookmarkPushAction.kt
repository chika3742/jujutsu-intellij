package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.ui.push.launchJjPushDialog

/**
 * Opens the push preview dialog for [bookmarkName] on the selected commit's repository — the same
 * [net.chikach.jujutsuintellij.ui.push.JjPushDialog] as the toolbar push, but scoped to this one
 * bookmark. Used as the "Push..." item inside a local bookmark's submenu.
 */
class JjLogBookmarkPushAction(private val bookmarkName: String) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.Bookmark.item.push")
        templatePresentation.description = JujutsuBundle.message("action.Jujutsu.Log.BookmarkPush.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return
        launchJjPushDialog(project, listOf(target.repo), bookmarkFilter = setOf(bookmarkName))
    }
}
