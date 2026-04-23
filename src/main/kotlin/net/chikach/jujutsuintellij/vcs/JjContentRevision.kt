package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.model.JjRevisionNumber
import net.chikach.jujutsuintellij.repo.JjRepository

/**
 * Lazily produces the content of a file at a specific jj revision via `jj file show -r <rev>`.
 * The process is spawned only when [getContent] is first invoked, so the Local Changes view
 * never blocks on a shell-out while building its list.
 */
class JjContentRevision(
    private val repo: JjRepository,
    relativePath: String,
    private val revision: String,
) : ContentRevision {

    private val normalizedRelative: String = repo.normalizeRelativePath(relativePath)
    private val filePath: FilePath =
        VcsUtil.getFilePath(repo.resolveRelativePath(normalizedRelative), false)

    @Throws(VcsException::class)
    override fun getContent(): String {
        val result = try {
            JjCommands.getInstance().showFile(repo, revision, normalizedRelative)
        } catch (e: Exception) {
            throw VcsException("Failed to read $normalizedRelative at $revision: ${e.message}", e)
        }
        if (!result.isSuccess) {
            throw VcsException(
                "jj file show -r $revision $normalizedRelative exited ${result.exitCode}: ${result.stderr.trim()}"
            )
        }
        return result.stdout
    }

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = JjRevisionNumber(revision)
}
