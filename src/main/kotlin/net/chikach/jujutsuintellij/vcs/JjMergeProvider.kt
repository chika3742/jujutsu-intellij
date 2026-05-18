package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.nio.charset.StandardCharsets

/**
 * Drives IDE's built-in 3-way merge tool against a jj working copy in a conflicted state.
 *
 * For a merge commit with parents `p0` and `p1`, CURRENT and LAST are the file contents at
 * each parent; ORIGINAL is the LCA (`heads(ancestors(p0) & ancestors(p1))`) when resolvable
 * by jj, else falls back to `p0`. Binary files are excluded — IDE's merge tool is text-only.
 */
@Service(Service.Level.PROJECT)
class JjMergeProvider(private val project: Project) : MergeProvider {

    @Throws(VcsException::class)
    override fun loadRevisions(file: VirtualFile): MergeData {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForFile(file)
            ?: throw VcsException("Not under a Jujutsu repository: ${file.path}")
        val rel = repo.relativize(file.path)
            ?: throw VcsException("Cannot relativize ${file.path}")
        val wc = repo.workingCopyCommit()
            ?: throw VcsException("Cannot read jj working-copy commit")
        val parents = wc.parentIds

        fun bytesOrEmpty(revision: String): ByteArray = runCatching {
            repo.showFile(revision, rel).toByteArray(StandardCharsets.UTF_8)
        }.getOrDefault(ByteArray(0))

        return MergeData().apply {
            if (parents.size >= 2) {
                CURRENT = bytesOrEmpty(parents[0])
                LAST = bytesOrEmpty(parents[1])
                val lca = bytesOrEmpty("heads(ancestors(${parents[0]}) & ancestors(${parents[1]}))")
                ORIGINAL = if (lca.isNotEmpty()) lca else bytesOrEmpty(parents[0])
            } else {
                val parent = parents.firstOrNull() ?: JjRepository.FIRST_PARENT_REF
                val parentBytes = bytesOrEmpty(parent)
                CURRENT = parentBytes
                LAST = parentBytes
                ORIGINAL = parentBytes
            }
        }
    }

    override fun conflictResolvedForFile(file: VirtualFile) {
        // jj auto-snapshots the resolved file on the next CLI invocation. We just need to
        // make the IDE re-read the on-disk content and refresh Local Changes.
        file.refresh(false, false)
        VcsDirtyScopeManager.getInstance(project).fileDirty(file)
    }

    override fun isBinary(file: VirtualFile): Boolean = file.fileType.isBinary
}
