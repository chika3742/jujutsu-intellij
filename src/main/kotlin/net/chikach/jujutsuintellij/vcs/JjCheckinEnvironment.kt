package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.jujutsuintellij.repo.JjOperationException
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.JjWorkingCopyDescription

/**
 * Maps IntelliJ's "Commit" action onto `jj commit -m <message>`.
 *
 * File selection in the Commit tool window is intentionally ignored: jj auto-tracks all working-copy
 * changes into `@`, so partial-file commits require `jj split` (a Phase-6 feature).
 */
@Service(Service.Level.PROJECT)
class JjCheckinEnvironment(private val project: Project) : CheckinEnvironment {

    override fun getCheckinOperationName(): String = "Commit"

    override fun getHelpId(): String? = null

    override fun scheduleMissingFileForDeletion(files: List<FilePath>): List<VcsException>? = null

    override fun scheduleUnversionedFilesForAddition(files: List<VirtualFile>): List<VcsException>? = null

    override fun isRefreshAfterCommitNeeded(): Boolean = true

    override fun commit(
        changes: List<Change>,
        commitMessage: String,
        commitContext: CommitContext,
        feedback: MutableSet<in String>,
    ): List<VcsException>? {
        if (commitMessage.isBlank()) {
            return listOf(VcsException("Commit message cannot be empty"))
        }

        val repos = findRepos(changes)
        if (repos.isEmpty()) {
            return listOf(VcsException("No Jujutsu repository found for the selected changes"))
        }

        val errors = mutableListOf<VcsException>()

        for (repo in repos) {
            try {
                repo.commit(commitMessage)
            } catch (e: JjOperationException) {
                errors += VcsException("jj commit failed: ${e.message}", e)
            }
        }

        JjWorkingCopyDescription.getInstance(project).refresh()
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
        return errors.ifEmpty { null }
    }

    private fun findRepos(changes: List<Change>): List<JjRepository> {
        val manager = JjRepositoryManager.getInstance(project)
        return changes
            .mapNotNull { change ->
                val path = (change.afterRevision ?: change.beforeRevision)?.file?.path ?: return@mapNotNull null
                manager.getAll().filter { it.containsPath(path) }.maxByOrNull { it.rootPath.length }
            }
            .distinctBy { it.root }
    }
}
