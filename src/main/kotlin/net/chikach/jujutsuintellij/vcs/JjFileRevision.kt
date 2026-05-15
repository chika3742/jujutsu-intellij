package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.RepositoryLocation
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import net.chikach.jujutsuintellij.model.JjRevisionNumber
import net.chikach.jujutsuintellij.repo.JjOperationException
import net.chikach.jujutsuintellij.repo.JjRepository
import java.util.*

/**
 * A single jj commit as reported by `jj log` for a specific file. Exposes the jj-side metadata
 * (commit_id, change_id, author, timestamp, description) and lazily loads the file's content
 * at that commit via `jj file show -r <commit_id> <relative>`.
 */
class JjFileRevision(
    private val repo: JjRepository,
    private val filePath: FilePath,
    private val relativePath: String,
    val commitId: String,
    val changeId: String,
    private val author: String,
    private val date: Date,
    private val message: String,
) : VcsFileRevision {

    override fun getRevisionNumber(): VcsRevisionNumber = JjRevisionNumber(commitId)

    override fun getRevisionDate(): Date = date

    override fun getAuthor(): String = author

    override fun getCommitMessage(): String = message

    override fun getBranchName(): String? = null

    override fun getChangedRepositoryPath(): RepositoryLocation? = null

    @Deprecated("Use loadContent() instead", ReplaceWith("loadContent()"))
    override fun getContent(): ByteArray? = loadContent()

    override fun loadContent(): ByteArray? {
        val text = try {
            repo.showFile(commitId, relativePath)
        } catch (e: JjOperationException) {
            // File did not exist at that revision — surface as null so diff renders an empty side.
            return null
        } catch (e: Exception) {
            throw VcsException("Failed to read $relativePath at $commitId: ${e.message}", e)
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    fun getFilePath(): FilePath = filePath
}
