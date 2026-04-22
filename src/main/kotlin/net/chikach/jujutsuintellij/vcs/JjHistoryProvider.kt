package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner
import com.intellij.openapi.vcs.history.VcsDependentHistoryComponents
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ColumnInfo
import net.chikach.jujutsuintellij.cli.JjCli
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JComponent

/**
 * Drives IntelliJ's "Show History" panel from `jj log`.
 *
 * The filter uses the positional `FILESETS` argument so jj shows only commits that actually
 * modified the file, and the template emits machine-readable fields separated by ASCII 0x1f /
 * 0x1e so multiline commit descriptions round-trip safely.
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
        val result = try {
            JjCli.getInstance().execute(
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
        if (!result.isSuccess) {
            throw VcsException("jj log exited ${result.exitCode}: ${result.stderr.trim()}")
        }

        val out = result.stdout
        if (out.isEmpty()) return emptyList()

        val revisions = ArrayList<VcsFileRevision>()
        for (record in out.split(RS)) {
            if (record.isEmpty()) continue
            val fields = record.split(FS)
            if (fields.size < 6) {
                if (LOG.isDebugEnabled) LOG.debug("Malformed jj log record: $record")
                continue
            }
            val commitId = fields[0]
            val changeId = fields[1]
            val authorName = fields[2]
            val authorEmail = fields[3]
            val timestamp = fields[4]
            val description = fields[5]

            val date = parseTimestamp(timestamp) ?: Date(0)
            val author = when {
                authorName.isNotBlank() && authorEmail.isNotBlank() -> "$authorName <$authorEmail>"
                authorName.isNotBlank() -> authorName
                else -> authorEmail
            }
            revisions += JjFileRevision(
                repo = repo,
                filePath = filePath,
                relativePath = relative,
                commitId = commitId,
                changeId = changeId,
                author = author,
                date = date,
                message = description.trimEnd('\n'),
            )
        }
        return revisions
    }

    private fun parseTimestamp(raw: String): Date? {
        if (raw.isBlank()) return null
        for (format in TIMESTAMP_FORMATS) {
            try {
                return SimpleDateFormat(format, Locale.ROOT).parse(raw)
            } catch (_: Exception) {
                // try next
            }
        }
        if (LOG.isDebugEnabled) LOG.debug("Unparseable jj timestamp: $raw")
        return null
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
        private val LOG = logger<JjHistoryProvider>()
        private const val FS = ""
        private const val RS = ""

        /**
         * Emits one record per commit. Separators are raw ASCII control bytes passed literally
         * through to jj's template parser, which treats them as ordinary characters inside the
         * double-quoted string literals.
         */
        private val HISTORY_TEMPLATE =
            "commit_id ++ \"$FS\" ++ change_id ++ \"$FS\" ++ author.name() ++ \"$FS\" ++ " +
                "author.email() ++ \"$FS\" ++ author.timestamp() ++ \"$FS\" ++ description ++ \"$RS\""

        private val TIMESTAMP_FORMATS = listOf(
            "yyyy-MM-dd HH:mm:ss.SSS XXX",
            "yyyy-MM-dd HH:mm:ss XXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
    }
}
