package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ColumnInfo
import net.chikach.jujutsuintellij.cli.JjCli
import net.chikach.jujutsuintellij.cli.JjJsonCommand
import net.chikach.jujutsuintellij.cli.JjJsonDecoders
import net.chikach.jujutsuintellij.cli.template.*
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.nio.file.Paths
import javax.swing.JComponent

/**
 * Drives IntelliJ's "Show History" panel from `jj log`.
 *
     * The filter uses the positional `FILESETS` argument so jj shows only commits that actually
     * modified the file, and the template emits one JSON object per line so multiline descriptions
     * remain machine-readable without bespoke field separators.
 */
@Service(Service.Level.PROJECT)
class JjHistoryProvider(private val project: Project) : VcsHistoryProvider {

    override fun getUICustomization(
        session: VcsHistorySession,
        forShortcutRegistration: JComponent,
    ): VcsDependentHistoryComponents =
        VcsDependentHistoryComponents.createOnlyColumns(ColumnInfo.EMPTY_ARRAY)

    override fun getAdditionalActions(refresher: Runnable): Array<AnAction> = AnAction.EMPTY_ARRAY

    override fun isDateOmittable(): Boolean = false

    override fun getHelpId(): String? = null

    override fun createSessionFor(filePath: FilePath): VcsHistorySession? {
        val repo = findRepo(filePath) ?: return null
        val relative = relativize(repo, filePath.path) ?: return null
        val revisions = collectHistory(repo, filePath, relative)
        val current = revisions.firstOrNull()?.revisionNumber
        return JjHistorySession(filePath, current, revisions)
    }

    override fun reportAppendableHistory(path: FilePath, partner: VcsAppendableHistorySessionPartner) {
        val repo = findRepo(path) ?: return
        val relative = relativize(repo, path.path) ?: return

        val emptySession = JjHistorySession(path, null, emptyList())
        partner.reportCreatedEmptySession(emptySession)

        try {
            val revisions = collectHistory(repo, path, relative)
            for (revision in revisions) {
                partner.acceptRevision(revision)
            }
        } catch (e: VcsException) {
            partner.reportException(e)
        }
    }

    override fun supportsHistoryForDirectories(): Boolean = false

    override fun getHistoryDiffHandler(): DiffFromHistoryHandler? = null

    override fun canShowHistoryFor(file: VirtualFile): Boolean {
        if (!file.isValid) return false
        if (file.isDirectory) return false
        val vcsManager = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project)
        val vcs = vcsManager.getVcsFor(file) ?: return false
        return vcs.name == JujutsuVcs.VCS_NAME
    }

    private fun findRepo(filePath: FilePath): JjRepository? {
        val manager = JjRepositoryManager.getInstance(project)
        filePath.virtualFile?.let { vf ->
            manager.getRepositoryForFile(vf)?.let { return it }
        }
        val absolute = filePath.path
        return manager.getAll()
            .filter { absolute == it.rootPath || absolute.startsWith(if (it.rootPath.endsWith('/')) it.rootPath else it.rootPath + '/') }
            .maxByOrNull { it.rootPath.length }
    }

    private fun relativize(repo: JjRepository, absolutePath: String): String? {
        val rootPath = repo.rootPath
        if (absolutePath == rootPath) return ""
        val prefix = if (rootPath.endsWith('/')) rootPath else "$rootPath/"
        if (!absolutePath.startsWith(prefix)) return null
        return absolutePath.substring(prefix.length)
    }

    private fun collectHistory(
        repo: JjRepository,
        filePath: FilePath,
        relative: String,
    ): List<VcsFileRevision> {
        val records = try {
            JjJsonCommand.getInstance().executeObjects(
                JjCli.Request(
                    workDir = Paths.get(repo.rootPath),
                    args = listOf(
                        "log",
                        "--no-graph",
                        "-r", "::@",
                        "-T", HISTORY_TEMPLATE,
                        "--",
                        relative,
                    ),
                )
            )
        } catch (e: Exception) {
            throw VcsException("jj log failed for $relative: ${e.message}", e)
        }
        return JjJsonDecoders.decodeHistoryEntries(records).map { entry ->
            JjFileRevision(
                repo = repo,
                filePath = filePath,
                relativePath = relative,
                commitId = entry.commitId,
                changeId = entry.changeId,
                author = entry.author,
                date = entry.date,
                message = entry.description,
            )
        }
    }

    private class JjHistorySession(
        private val filePath: FilePath,
        private val currentRevision: VcsRevisionNumber?,
        revisions: List<VcsFileRevision>,
    ) : VcsAbstractHistorySession(revisions, currentRevision) {

        override fun calcCurrentRevisionNumber(): VcsRevisionNumber? = currentRevision

        override fun copy(): VcsHistorySession =
            JjHistorySession(filePath, currentRevisionNumber, revisionList)
    }

    companion object {
        private val HISTORY_TEMPLATE =
            JjTemplates.commitJsonLine {
                obj {
                    "commitId" to string(commitId)
                    "changeId" to string(changeId)
                    "authorName" to string(author.name())
                    "authorEmail" to string(author.email())
                    "timestamp" to string(author.timestamp())
                    "description" to string(description)
                }
            }
    }
}
