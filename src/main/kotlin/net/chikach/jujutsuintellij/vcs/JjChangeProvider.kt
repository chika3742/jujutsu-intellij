package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.*
import com.intellij.vcsUtil.VcsUtil
import net.chikach.jujutsuintellij.cli.GitIgnoredScanner
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.model.JjFileChange
import java.io.File

/**
 * Populates IntelliJ's Local Changes view from `jj diff --summary --from first_parent(@) --to @`. Each
 * reported file becomes a [Change] whose `afterRevision` reflects the on-disk (working-copy)
 * state and whose `beforeRevision` lazily reads the parent commit via `jj file show`.
 *
 * After the CLI-derived changes are reported, any in-memory modified Documents that jj has
 * not yet seen (because the snapshot happens on CLI invocation against disk state) are
 * synthesized as MODIFIED changes — mirroring git4idea's `NonChangedHolder` trick so the
 * Commit tool window updates while the user is still typing.
 */
@Service(Service.Level.PROJECT)
class JjChangeProvider(private val project: Project) : ChangeProvider {

    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate,
    ) {
        val manager = JjRepositoryManager.getInstance(project)
        val repos = dirtyScope.affectedContentRoots
            .mapNotNull { manager.getRepositoryForFile(it) }
            .distinctBy { it.root }

        val processedPaths = HashSet<FilePath>()

        for (repo in repos) {
            progress.checkCanceled()
            processRepository(repo, builder, progress, processedPaths)
        }

        progress.checkCanceled()
        reportUnsavedDocuments(dirtyScope, builder, addGate, processedPaths)
    }

    private fun processRepository(
        repo: JjRepository,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        processedPaths: MutableSet<FilePath>,
    ) {
        val changes = try {
            repo.workingCopyChanges()
        } catch (e: Exception) {
            LOG.warn("jj diff failed in ${repo.rootPath}", e)
            return
        }

        val conflicted = try {
            repo.workingCopyConflictedFiles().mapTo(HashSet()) { repo.normalizeRelativePath(it) }
        } catch (e: Exception) {
            LOG.warn("jj conflict listing failed in ${repo.rootPath}", e)
            emptySet()
        }

        for (change in changes) {
            progress.checkCanceled()
            reportChange(change, repo, builder, processedPaths, conflicted)
        }

        reportIgnoredFiles(repo, builder, progress, processedPaths)
    }

    private fun reportChange(
        change: JjFileChange,
        repo: JjRepository,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
        conflicted: Set<String>,
    ) {
        if (repo.normalizeRelativePath(change.path) in conflicted) {
            reportConflicted(repo, change.path, builder, processedPaths)
            return
        }
        when (change.status) {
            JjFileChange.Status.ADDED -> reportAdded(repo, change.path, builder, processedPaths)
            JjFileChange.Status.MODIFIED -> reportModified(repo, change.path, builder, processedPaths)
            JjFileChange.Status.DELETED -> reportDeleted(repo, change.path, builder, processedPaths)
            JjFileChange.Status.RENAMED -> reportRenameOrCopy(
                repo, change.sourcePath!!, change.path, builder, processedPaths, isCopy = false,
            )
            JjFileChange.Status.COPIED -> reportRenameOrCopy(
                repo, change.sourcePath!!, change.path, builder, processedPaths, isCopy = true,
            )
        }
    }

    /**
     * For co-located repos, ask git for the set of files excluded by `.gitignore` and surface
     * them to IntelliJ as ignored. Non-colocated repos skip this (no `.git` directory to query).
     */
    private fun reportIgnoredFiles(
        repo: JjRepository,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        processedPaths: MutableSet<FilePath>,
    ) {
        val relatives = try {
            GitIgnoredScanner.scan(repo.rootPathNio)
        } catch (e: Exception) {
            LOG.warn("Ignored-file scan failed in ${repo.rootPath}", e)
            return
        }
        for (relative in relatives) {
            progress.checkCanceled()
            val file = File(repo.rootPath, relative)
            val filePath = VcsUtil.getFilePath(file, file.isDirectory)
            if (!processedPaths.add(filePath)) continue
            builder.processIgnoredFile(filePath)
        }
    }

    private fun reportAdded(
        repo: JjRepository,
        relative: String,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
    ) {
        val (filePath, after) = currentContent(repo, relative)
        processedPaths += filePath
        builder.processChange(Change(null, after, FileStatus.ADDED), JujutsuVcs.KEY)
    }

    private fun reportModified(
        repo: JjRepository,
        relative: String,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
    ) {
        val before = JjContentRevision(repo, relative, JjRepository.WORKING_COPY_FIRST_PARENT_REVSET)
        val (filePath, after) = currentContent(repo, relative)
        processedPaths += filePath
        builder.processChange(Change(before, after, FileStatus.MODIFIED), JujutsuVcs.KEY)
    }

    /**
     * A file in a conflicted state at `@`. Reported as [FileStatus.MERGED_WITH_CONFLICTS] so the
     * IDE shows the "Resolve Conflicts" affordance and routes the double-click to the 3-way merge
     * tool ([JjMergeProvider]) instead of a plain diff of the materialized conflict markers.
     */
    private fun reportConflicted(
        repo: JjRepository,
        relative: String,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
    ) {
        val before = JjContentRevision(repo, relative, JjRepository.WORKING_COPY_FIRST_PARENT_REVSET)
        val (filePath, after) = currentContent(repo, relative)
        processedPaths += filePath
        builder.processChange(Change(before, after, FileStatus.MERGED_WITH_CONFLICTS), JujutsuVcs.KEY)
    }

    private fun reportDeleted(
        repo: JjRepository,
        relative: String,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
    ) {
        val before = JjContentRevision(repo, relative, JjRepository.WORKING_COPY_FIRST_PARENT_REVSET)
        processedPaths += before.file
        builder.processChange(Change(before, null, FileStatus.DELETED), JujutsuVcs.KEY)
    }

    private fun reportRenameOrCopy(
        repo: JjRepository,
        oldRelative: String,
        newRelative: String,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
        isCopy: Boolean,
    ) {
        val before = JjContentRevision(repo, oldRelative, JjRepository.WORKING_COPY_FIRST_PARENT_REVSET)
        val (newFilePath, after) = currentContent(repo, newRelative)
        processedPaths += before.file
        processedPaths += newFilePath
        // Local Changes uses Change(before, after) with non-matching file paths to render as rename.
        // Copies are modeled as addition whose `beforeRevision` points at the source so diff still works.
        builder.processChange(
            Change(before, after, if (isCopy) FileStatus.ADDED else FileStatus.MODIFIED),
            JujutsuVcs.KEY,
        )
    }

    private fun currentContent(repo: JjRepository, relative: String): Pair<FilePath, CurrentContentRevision> {
        val filePath = VcsUtil.getFilePath(repo.resolveRelativePath(relative), false)
        return filePath to CurrentContentRevision(filePath)
    }

    /**
     * For any Document that is modified in memory but has not yet been persisted to disk,
     * synthesize a MODIFIED change so the Commit tool window reflects the edit immediately.
     * Skips files already reported from `jj diff`, files outside any jj root, and entries the
     * IDE has already marked with a non-null status (ignored, added-by-other-provider, etc.).
     */
    private fun reportUnsavedDocuments(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        addGate: ChangeListManagerGate,
        processedPaths: Set<FilePath>,
    ) {
        val manager = JjRepositoryManager.getInstance(project)
        val fdm = FileDocumentManager.getInstance()
        for (document in fdm.unsavedDocuments) {
            val file = fdm.getFile(document) ?: continue
            if (!file.isValid || file.isDirectory) continue
            if (!fdm.isFileModified(file)) continue
            if (addGate.getStatus(file) != null) continue

            val filePath = VcsUtil.getFilePath(file)
            if (filePath in processedPaths) continue
            if (!dirtyScope.belongsTo(filePath)) continue

            val repo = manager.getRepositoryForFile(file) ?: continue
            val relative = repo.relativize(file.path) ?: continue

            val before = JjContentRevision(repo, relative, JjRepository.WORKING_COPY_FIRST_PARENT_REVSET)
            val after = CurrentContentRevision(filePath)
            builder.processChange(Change(before, after, FileStatus.MODIFIED), JujutsuVcs.KEY)
        }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = true

    companion object {
        private val LOG = logger<JjChangeProvider>()
    }
}
