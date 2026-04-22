package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.diff.ItemLatestState
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.jujutsuintellij.model.JjRevisionNumber
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager

/**
 * Supplies the IDE's diff infrastructure (gutter markers, "Show Diff Against …", "Compare with
 * Branch") with per-file revision metadata and lazy content revisions.
 *
 * Jujutsu auto-snapshots the working copy into `@` on every CLI invocation, so the conceptual
 * "HEAD" to diff against is the parent commit `@-`. We therefore treat `@-` as both the current
 * and latest committed revision — the working tree is compared against it.
 */
@Service(Service.Level.PROJECT)
class JjDiffProvider(private val project: Project) : DiffProvider {

    override fun getCurrentRevision(file: VirtualFile): VcsRevisionNumber? {
        repoFor(file) ?: return null
        return JjRevisionNumber.WORKING_COPY_PARENT
    }

    override fun getLastRevision(virtualFile: VirtualFile): ItemLatestState? {
        repoFor(virtualFile) ?: return null
        return ItemLatestState(JjRevisionNumber.WORKING_COPY_PARENT, true, true)
    }

    override fun getLastRevision(filePath: FilePath): ItemLatestState? {
        val vf = filePath.virtualFile ?: return fallbackLastRevision(filePath)
        return getLastRevision(vf)
    }

    override fun createFileContent(
        revisionNumber: VcsRevisionNumber,
        selectedFile: VirtualFile,
    ): ContentRevision? {
        val repo = repoFor(selectedFile) ?: return null
        val relative = relativize(repo, selectedFile.path) ?: run {
            if (LOG.isDebugEnabled) LOG.debug("File ${selectedFile.path} is not under ${repo.rootPath}")
            return null
        }
        return JjContentRevision(repo, relative, revisionNumber.asString())
    }

    override fun getLatestCommittedRevision(vcsRoot: VirtualFile): VcsRevisionNumber {
        return JjRevisionNumber.WORKING_COPY_PARENT
    }

    private fun fallbackLastRevision(filePath: FilePath): ItemLatestState? {
        val manager = JjRepositoryManager.getInstance(project)
        for (repo in manager.getAll()) {
            if (filePath.path.startsWith(repo.rootPath)) {
                return ItemLatestState(JjRevisionNumber.WORKING_COPY_PARENT, true, true)
            }
        }
        return null
    }

    private fun repoFor(file: VirtualFile): JjRepository? =
        JjRepositoryManager.getInstance(project).getRepositoryForFile(file)

    private fun relativize(repo: JjRepository, absolutePath: String): String? {
        val rootPath = repo.rootPath
        if (absolutePath == rootPath) return ""
        val prefix = if (rootPath.endsWith('/')) rootPath else "$rootPath/"
        if (!absolutePath.startsWith(prefix)) return null
        return absolutePath.substring(prefix.length)
    }

    companion object {
        private val LOG = logger<JjDiffProvider>()
    }
}
