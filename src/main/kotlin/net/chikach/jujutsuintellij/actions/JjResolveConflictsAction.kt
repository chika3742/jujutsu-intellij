package net.chikach.jujutsuintellij.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogDataKeys
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.caches.JjWorkingCopyCache
import net.chikach.jujutsuintellij.repo.model.JjCommit
import net.chikach.jujutsuintellij.vcs.JjConflictTracker
import net.chikach.jujutsuintellij.vcs.JjMergeProvider

/**
 * Brings a conflicted jj commit into `@` (when a non-`@` commit is selected in the VCS Log)
 * and opens IntelliJ's 3-way merge tool on the resulting working-copy conflicts.
 *
 * Entry points: VCS Log right-click, Jujutsu menu, status-bar popup.
 */
class JjResolveConflictsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return
        val target = selectedCommitHash(e)

        object : Task.Backgroundable(project, "Resolving Jujutsu Conflicts") {
            override fun run(indicator: ProgressIndicator) {
                var wc = repo.workingCopyCommit()
                if (target != null && target != wc?.commitId) {
                    repo.newChange(target)
                    wc = repo.workingCopyCommit() // @ moved; re-read for the new conflicts
                }
                if (wc == null || wc.conflictedFiles.isEmpty()) {
                    notifyNoConflicts(project)
                    return
                }
                val files = resolveFiles(repo, wc)
                if (files.isEmpty()) {
                    notifyNoConflicts(project)
                    return
                }
                ApplicationManager.getApplication().invokeLater {
                    AbstractVcsHelper.getInstance(project)
                        .showMergeDialog(files, project.service<JjMergeProvider>())
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                    JjWorkingCopyCache.getInstance(project).refresh()
                }
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message, "Resolve Conflicts Failed")
            }
        }.queue()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || findRepo(e) == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        if (e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) != null) {
            val hash = selectedCommitHash(e)
            e.presentation.isEnabledAndVisible =
                hash != null && JjConflictTracker.getInstance(project).isConflicted(hash)
            return
        }
        // Menu / status-bar context: stay enabled; the background task short-circuits with a
        // balloon when `@` has no conflicts.
        e.presentation.isEnabledAndVisible = true
    }

    private fun findRepo(e: AnActionEvent): JjRepository? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val manager = JjRepositoryManager.getInstance(project)
        return if (file != null) manager.getRepositoryForFile(file)
        else manager.getAll().firstOrNull()
    }

    private fun selectedCommitHash(e: AnActionEvent): String? =
        e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
            ?.commits
            ?.singleOrNull()
            ?.hash
            ?.asString()

    private fun resolveFiles(repo: JjRepository, wc: JjCommit): List<VirtualFile> {
        val lfs = LocalFileSystem.getInstance()
        return wc.conflictedFiles.mapNotNull { rel ->
            val nio = repo.resolveRelativePath(rel).toPath()
            lfs.refreshAndFindFileByNioFile(nio)
        }
    }

    private fun notifyNoConflicts(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Jujutsu")
            .createNotification("No conflicts to resolve at @", NotificationType.INFORMATION)
            .notify(project)
    }
}
