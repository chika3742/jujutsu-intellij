package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import net.chikach.jujutsuintellij.cli.JjCli
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives IntelliJ's "Annotate with Git Blame" equivalent for jj. Annotation is produced by
 * `jj file annotate` against the working copy (`@`) — each line is the commit that last
 * touched it.
 *
 * Parsing uses ASCII 0x1F (unit separator) between fields and 0x1E (record separator) between
 * annotation records. Using the content's own `\n` as a separator made the record count
 * disagree with IntelliJ's Document line count whenever the source had blank lines or lacked
 * a trailing newline, so a dedicated terminator is used instead.
 */
@Service(Service.Level.PROJECT)
class JjAnnotationProvider(private val project: Project) : AnnotationProvider {

    @Throws(VcsException::class)
    override fun annotate(file: VirtualFile): FileAnnotation = annotate(file, null)

    @Throws(VcsException::class)
    override fun annotate(file: VirtualFile, revision: VcsFileRevision?): FileAnnotation {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForFile(file)
            ?: throw VcsException("File is not under a Jujutsu repository: ${file.path}")
        val relative = relativize(repo, file.path)
            ?: throw VcsException("Cannot compute path for ${file.path} inside ${repo.rootPath}")

        val args = buildList {
            add("file")
            add("annotate")
            add("-T")
            add(TEMPLATE)
            if (revision != null) {
                add("-r")
                add(revision.revisionNumber.asString())
            }
            add(relative)
        }

        val result = try {
            JjCli.getInstance().execute(
                JjCli.Request(workDir = Paths.get(repo.rootPath), args = args)
            )
        } catch (e: Exception) {
            throw VcsException("Failed to run jj file annotate on $relative: ${e.message}", e)
        }
        if (!result.isSuccess) {
            throw VcsException("jj file annotate exited ${result.exitCode}: ${result.stderr.trim()}")
        }

        val lines = parse(result.stdout)
        val annotatedContent = String(file.contentsToByteArray(), file.charset)
        val revisions = buildRevisionsList(repo, relative, file, lines)
        return JjFileAnnotation(project, file, annotatedContent, lines, revisions)
    }

    private fun parse(stdout: String): List<JjAnnotationLine> {
        val lines = ArrayList<JjAnnotationLine>()
        for (record in stdout.split(RS)) {
            if (record.isEmpty()) continue
            val fields = record.split(FS)
            if (fields.size < 6) {
                if (LOG.isDebugEnabled) LOG.debug("Malformed jj annotate record: $record")
                continue
            }
            val commitId = fields[0]
            val changeId = fields[1]
            val authorName = fields[2]
            val authorEmail = fields[3]
            val timestamp = fields[4]
            // fields[5] = line_number (unused — we rely on position); content is dropped,
            // IntelliJ uses the source VirtualFile's text for display.

            val date = parseTimestamp(timestamp) ?: Date(0)
            lines += JjAnnotationLine(commitId, changeId, authorName, authorEmail, date)
        }
        return lines
    }

    private fun buildRevisionsList(
        repo: JjRepository,
        relative: String,
        file: VirtualFile,
        lines: List<JjAnnotationLine>,
    ): List<VcsFileRevision> {
        val seen = LinkedHashMap<String, JjAnnotationLine>()
        for (line in lines) {
            seen.putIfAbsent(line.commitId, line)
        }
        val filePath = VcsUtil.getFilePath(file)
        return seen.values.map { line ->
            val author = when {
                line.authorName.isNotBlank() && line.authorEmail.isNotBlank() -> "${line.authorName} <${line.authorEmail}>"
                line.authorName.isNotBlank() -> line.authorName
                else -> line.authorEmail
            }
            JjFileRevision(
                repo = repo,
                filePath = filePath,
                relativePath = relative,
                commitId = line.commitId,
                changeId = line.changeId,
                author = author,
                date = line.date,
                message = "",
            )
        }
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
        return null
    }

    private fun relativize(repo: JjRepository, absolutePath: String): String? {
        val rootPath = repo.rootPath
        if (absolutePath == rootPath) return ""
        val prefix = if (rootPath.endsWith('/')) rootPath else "$rootPath/"
        if (!absolutePath.startsWith(prefix)) return null
        return absolutePath.substring(prefix.length)
    }

    companion object {
        private val LOG = logger<JjAnnotationProvider>()
        private const val FS = '\u001F'
        private const val RS = '\u001E'

        private val TEMPLATE =
            "self.commit().commit_id() ++ \"$FS\" ++ " +
                "self.commit().change_id() ++ \"$FS\" ++ " +
                "self.commit().author().name() ++ \"$FS\" ++ " +
                "self.commit().author().email() ++ \"$FS\" ++ " +
                "self.commit().author().timestamp() ++ \"$FS\" ++ " +
                "self.line_number() ++ \"$RS\""

        private val TIMESTAMP_FORMATS = listOf(
            "yyyy-MM-dd HH:mm:ss.SSS XXX",
            "yyyy-MM-dd HH:mm:ss XXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
    }
}
