package net.chikach.jujutsuintellij.ui.push

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.actions.notifyJjInfo
import net.chikach.jujutsuintellij.actions.runJjInBackground
import net.chikach.jujutsuintellij.caches.JjBookmarkCache
import net.chikach.jujutsuintellij.repo.JjRepository

/**
 * Opens the [JjPushDialog] for [repos] and pushes the user's confirmed selection.
 *
 * The preview inputs are prepared off the EDT — the bookmark cache (the source of truth for the
 * target comparison) is refreshed and each repo's remotes are listed — then the dialog is shown on
 * the EDT. Shared by the toolbar push and the VCS Log "Push <bookmark>" actions so both use the same
 * dialog. When [bookmarkFilter] is non-null, the preview and push are restricted to those bookmark
 * names (the VCS Log action pushes only the bookmark picked from the context menu).
 */
fun launchJjPushDialog(project: Project, repos: List<JjRepository>, bookmarkFilter: Set<String>? = null) {
    if (repos.isEmpty()) return
    object : Task.Backgroundable(project, JujutsuBundle.message("dialog.push.prepare")) {
        private lateinit var inputs: List<RepoPushInput>

        override fun run(indicator: ProgressIndicator) {
            JjBookmarkCache.getInstance(project).reload()
            val entries = JjBookmarkCache.getInstance(project).entries.associateBy { it.repo.rootPath }
            inputs = repos.mapNotNull { repo ->
                val entry = entries[repo.rootPath] ?: return@mapNotNull null
                val remotes = repo.listGitRemotes().filter { it != JjRepository.INTERNAL_GIT_REMOTE }
                RepoPushInput(repo, entry.refs, remotes, bookmarkFilter)
            }
        }

        override fun onSuccess() {
            if (inputs.none { it.remotes.isNotEmpty() }) {
                notifyJjInfo(project, JujutsuBundle.message("dialog.push.noRemotes"))
                return
            }
            val dialog = JjPushDialog(project, inputs)
            if (dialog.showAndGet()) {
                performPush(project, dialog.selections, dialog.pushTags, dialog.tagScope)
            }
        }

        override fun onThrowable(error: Throwable) {
            Messages.showErrorDialog(project, error.message, JujutsuBundle.message("dialog.bookmark.push.error"))
        }
    }.queue()
}

private fun performPush(
    project: Project,
    selections: List<RepoPushSelection>,
    pushTags: Boolean,
    tagScope: TagPushScope,
) {
    runJjInBackground(project, JujutsuBundle.message("dialog.push.task"), JujutsuBundle.message("dialog.bookmark.push.error")) {
        for (selection in selections) {
            if (selection.bookmarkNames.isNotEmpty()) {
                selection.repo.gitPush(
                    bookmarks = selection.bookmarkNames,
                    remote = selection.remote,
                    allowNew = selection.allowNew,
                )
            }
            if (pushTags) {
                val tags = when (tagScope) {
                    TagPushScope.ALL -> allLocalTags(selection.repo)
                    TagPushScope.ON_PUSHED -> tagsOnPushedBookmarks(selection.repo, selection.changes)
                }
                if (tags.isNotEmpty()) {
                    runCatching { selection.repo.gitPushTags(selection.remote, tags) }.onFailure { error ->
                        notifyJjWarning(project, JujutsuBundle.message("notification.push.tagsFailed", error.message ?: ""))
                    }
                }
            }
        }
        notifyJjInfo(project, JujutsuBundle.message("notification.push"))
    }
}

/** Shows a warning notification in the "Jujutsu" group (e.g. tag push failed after a successful push). */
private fun notifyJjWarning(project: Project, content: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Jujutsu")
        .createNotification(content, NotificationType.WARNING)
        .notify(project)
}
