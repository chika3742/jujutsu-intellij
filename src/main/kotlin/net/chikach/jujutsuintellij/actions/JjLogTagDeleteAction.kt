package net.chikach.jujutsuintellij.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsLogDataKeys
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache
import net.chikach.jujutsuintellij.repo.JjRepository

/**
 * Expands inline into one "Delete Tag '<name>'" item per local tag on the commit selected in the
 * VCS Log. Hidden when the selection has no tags.
 */
class JjLogTagDeleteActionGroup : ActionGroup() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = tagsAtSelection(e).isNotEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) return EMPTY_ARRAY
        return tagsAtSelection(e).map { JjLogTagDeleteAction(it) }.toTypedArray<AnAction>()
    }
}

/** Local tags on the single commit selected in the VCS Log, from [JjCommitCache]. */
private fun tagsAtSelection(e: AnActionEvent): List<String> {
    val project = e.project ?: return emptyList()
    val commit = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.singleOrNull() ?: return emptyList()
    return JjCommitCache.getInstance(project).get(commit.hash.asString())?.tags.orEmpty()
}

/**
 * Deletes [tagName] locally (`jj tag delete`) without a confirmation dialog, then offers follow-ups
 * on the completion notification:
 *
 * - **Delete on Remote(s)** — jj cannot delete a tag on a remote, so this shells out to
 *   `git push --delete` against every configured non-`git` remote (see
 *   [JjRepository.deleteRemoteTag]).
 * - **Undo** — recreates the tag at its old target (`jj tag set -r <commit>`). Unlike `jj undo`,
 *   this stays correct no matter how many other jj operations ran while the notification was open;
 *   if the tag was meanwhile recreated elsewhere, `jj tag set` refuses and the error surfaces.
 */
class JjLogTagDeleteAction(private val tagName: String) : JjLogCommitAction() {

    init {
        templatePresentation.text = JujutsuBundle.message("action.Jujutsu.Log.TagDelete.item.text", tagName)
        templatePresentation.description = JujutsuBundle.message("action.Jujutsu.Log.TagDelete.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.tag.delete.task"),
            JujutsuBundle.message("dialog.tag.delete.error"),
        ) {
            target.repo.deleteTag(tagName)
            notify(project, JujutsuBundle.message("notification.tag.delete", tagName)) {
                addAction(NotificationAction.createSimpleExpiring(
                    JujutsuBundle.message("notification.tag.delete.deleteOnRemotes")
                ) { deleteOnRemotes(project, target.repo) })
                addAction(NotificationAction.createSimpleExpiring(
                    JujutsuBundle.message("notification.tag.delete.undo")
                ) { undoDeletion(project, target) })
            }
        }
    }

    private fun deleteOnRemotes(project: Project, repo: JjRepository) {
        runInBackground(
            project,
            JujutsuBundle.message("dialog.tag.deleteOnRemotes.task"),
            JujutsuBundle.message("dialog.tag.delete.error"),
        ) {
            // jj only knows about remote tags after an explicit `jj git fetch --tag`, so the tag
            // listing cannot tell which remotes carry the tag. Delete on every configured remote
            // instead — `git push --delete` of a non-existent remote tag succeeds with a warning.
            val remotes = repo.listGitRemotes().filter { it != JjRepository.INTERNAL_GIT_REMOTE }
            if (remotes.isEmpty()) {
                notify(project, JujutsuBundle.message("notification.tag.delete.noRemotes"), NotificationType.WARNING)
                return@runInBackground
            }
            remotes.forEach { remote -> repo.deleteRemoteTag(tagName, remote) }
            notify(project, JujutsuBundle.message("notification.tag.deletedOnRemotes", tagName, remotes.joinToString(", ")))
        }
    }

    private fun undoDeletion(project: Project, target: Target) {
        runInBackground(
            project,
            JujutsuBundle.message("dialog.tag.undo.task"),
            JujutsuBundle.message("dialog.tag.undo.error"),
        ) {
            target.repo.setTag(tagName, target.hash)
            notify(project, JujutsuBundle.message("notification.tag.delete.undone", tagName))
        }
    }

    private fun notify(
        project: Project,
        message: String,
        type: NotificationType = NotificationType.INFORMATION,
        configure: Notification.() -> Unit = {},
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Jujutsu")
            .createNotification(message, type)
            .apply(configure)
            .notify(project)
    }
}
