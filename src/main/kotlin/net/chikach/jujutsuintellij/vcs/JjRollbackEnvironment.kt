package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.rollback.RollbackProgressListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.jujutsuintellij.repo.JjOperationException
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager

/**
 * Implements "Revert" in the Local Changes view via `jj restore --from @- <paths>`.
 *
 * Each reverted file is restored to its state in the parent commit `@-`. After all
 * restore calls complete, the affected files are refreshed in the VFS so the editor
 * reflects the on-disk content immediately.
 */
@Service(Service.Level.PROJECT)
class JjRollbackEnvironment(private val project: Project) : RollbackEnvironment {

    override fun getRollbackOperationName(): String = "Restore"

    override fun rollbackChanges(
        changes: List<Change>,
        vcsExceptions: List<VcsException>,
        listener: RollbackProgressListener,
    ) {
        val manager = JjRepositoryManager.getInstance(project)
        val changesByRepo = LinkedHashMap<JjRepository, MutableList<String>>()
        val filesToRefresh = mutableListOf<VirtualFile>()

        for (change in changes) {
            listener.accept(change)
            val revision = change.afterRevision ?: change.beforeRevision ?: continue
            val path = revision.file.path
            val repo = manager.getAll()
                .filter { it.containsPath(path) }
                .maxByOrNull { it.rootPath.length } ?: continue
            val relative = repo.relativize(path) ?: continue
            changesByRepo.getOrPut(repo) { mutableListOf() } += repo.normalizeRelativePath(relative)
            revision.file.virtualFile?.let { filesToRefresh += it }
        }

        @Suppress("UNCHECKED_CAST")
        val errors = vcsExceptions as MutableList<VcsException>
        for ((repo, paths) in changesByRepo) {
            try {
                repo.restore("@-", paths)
            } catch (e: JjOperationException) {
                errors += VcsException("jj restore failed: ${e.message}", e)
            }
        }

        LocalFileSystem.getInstance().refreshFiles(filesToRefresh, true, false, null)
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    }

    override fun rollbackMissingFileDeletion(
        files: List<FilePath>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        // Deletions are handled via rollbackChanges for jj
    }

    override fun rollbackModifiedWithoutCheckout(
        files: List<VirtualFile>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        // Not applicable for jj
    }
}
