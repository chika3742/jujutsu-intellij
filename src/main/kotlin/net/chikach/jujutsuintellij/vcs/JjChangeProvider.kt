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
import net.chikach.jujutsuintellij.cli.JjCli
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.io.File
import java.nio.file.Paths

/**
 * Populates IntelliJ's Local Changes view from `jj diff --summary --from @- --to @`. Each
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
        val result = try {
            JjCli.getInstance().execute(
                JjCli.Request(
                    workDir = Paths.get(repo.rootPath),
                    args = listOf("diff", "--summary", "--from", PARENT_REF, "--to", WORKING_COPY_REF),
                )
            )
        } catch (e: Exception) {
            LOG.warn("jj diff failed in ${repo.rootPath}", e)
            return
        }

        if (!result.isSuccess) {
            LOG.warn("jj diff exit=${result.exitCode} stderr=${result.stderr.trim()}")
            return
        }

        for (rawLine in result.stdout.lineSequence()) {
            progress.checkCanceled()
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) continue
            parseLine(line, repo, builder, processedPaths)
        }

        reportIgnoredFiles(repo, builder, progress, processedPaths)
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
            GitIgnoredScanner.scan(Paths.get(repo.rootPath))
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

    private fun parseLine(
        line: String,
        repo: JjRepository,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
    ) {
        if (line.length < 3) return
        val status = line[0]
        val body = line.substring(2)

        when (status) {
            'A' -> reportAdded(repo, body, builder, processedPaths)
            'M' -> reportModified(repo, body, builder, processedPaths)
            'D' -> reportDeleted(repo, body, builder, processedPaths)
            'R' -> reportRenameOrCopy(repo, body, builder, processedPaths, isCopy = false)
            'C' -> reportRenameOrCopy(repo, body, builder, processedPaths, isCopy = true)
            else -> if (LOG.isDebugEnabled) LOG.debug("Unknown jj diff status '$status' in: $line")
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
        val before = JjContentRevision(repo, relative, PARENT_REF)
        val (filePath, after) = currentContent(repo, relative)
        processedPaths += filePath
        builder.processChange(Change(before, after, FileStatus.MODIFIED), JujutsuVcs.KEY)
    }

    private fun reportDeleted(
        repo: JjRepository,
        relative: String,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
    ) {
        val before = JjContentRevision(repo, relative, PARENT_REF)
        processedPaths += before.file
        builder.processChange(Change(before, null, FileStatus.DELETED), JujutsuVcs.KEY)
    }

    private fun reportRenameOrCopy(
        repo: JjRepository,
        body: String,
        builder: ChangelistBuilder,
        processedPaths: MutableSet<FilePath>,
        isCopy: Boolean,
    ) {
        val (oldRelative, newRelative) = parseRenameBody(body) ?: run {
            if (LOG.isDebugEnabled) LOG.debug("Could not parse rename/copy body: $body")
            return
        }
        val before = JjContentRevision(repo, oldRelative, PARENT_REF)
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

    /**
     * jj renders renames and copies as one of:
     *   `prefix/{old => new}`  (common prefix factored out)
     *   `{old => new}`          (no common prefix)
     *   `old => new`            (older versions, unlikely)
     */
    private fun parseRenameBody(body: String): Pair<String, String>? {
        BRACE_RENAME.matchEntire(body)?.let { match ->
            val prefix = match.groupValues[1]
            val oldLeaf = match.groupValues[2]
            val newLeaf = match.groupValues[3]
            return (prefix + oldLeaf) to (prefix + newLeaf)
        }
        val arrow = body.indexOf(ARROW)
        if (arrow > 0) {
            return body.substring(0, arrow) to body.substring(arrow + ARROW.length)
        }
        return null
    }

    private fun currentContent(repo: JjRepository, relative: String): Pair<FilePath, CurrentContentRevision> {
        val normalized = relative.replace('\\', '/')
        val filePath = VcsUtil.getFilePath(File(repo.rootPath, normalized), false)
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
            val relative = relativize(repo, file.path) ?: continue

            val before = JjContentRevision(repo, relative, PARENT_REF)
            val after = CurrentContentRevision(filePath)
            builder.processChange(Change(before, after, FileStatus.MODIFIED), JujutsuVcs.KEY)
        }
    }

    private fun relativize(repo: JjRepository, absolutePath: String): String? {
        val rootPath = repo.rootPath
        if (absolutePath == rootPath) return ""
        val prefix = if (rootPath.endsWith('/')) rootPath else "$rootPath/"
        if (!absolutePath.startsWith(prefix)) return null
        return absolutePath.substring(prefix.length)
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = true

    companion object {
        private val LOG = logger<JjChangeProvider>()
        private const val PARENT_REF = "@-"
        private const val WORKING_COPY_REF = "@"
        private const val ARROW = " => "
        private val BRACE_RENAME = Regex("""^(.*?)\{(.*?) => (.*?)\}$""")
    }
}
