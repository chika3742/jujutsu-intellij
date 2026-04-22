package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import net.chikach.jujutsuintellij.cli.JjCli
import net.chikach.jujutsuintellij.cli.JjJsonCommand
import net.chikach.jujutsuintellij.cli.JjJsonDecoders
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.cli.template.JjTemplates
import net.chikach.jujutsuintellij.cli.template.email
import net.chikach.jujutsuintellij.cli.template.name
import net.chikach.jujutsuintellij.cli.template.num
import net.chikach.jujutsuintellij.cli.template.obj
import net.chikach.jujutsuintellij.cli.template.string
import net.chikach.jujutsuintellij.cli.template.timestamp
import java.nio.file.Paths

/**
 * Drives IntelliJ's "Annotate with Git Blame" equivalent for jj. Annotation is produced by
 * `jj file annotate` against the working copy (`@`) — each line is the commit that last
 * touched it.
 *
 * Parsing uses one JSON object per line. This keeps line-oriented output aligned with jj's own
 * annotation stream while avoiding ad-hoc delimiters inside author names or timestamps.
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

        val records = try {
            JjJsonCommand.getInstance().executeObjects(
                JjCli.Request(workDir = Paths.get(repo.rootPath), args = args)
            )
        } catch (e: Exception) {
            throw VcsException("Failed to run jj file annotate on $relative: ${e.message}", e)
        }
        val lines = JjJsonDecoders.decodeAnnotationEntries(records).map { entry ->
            JjAnnotationLine(
                commitId = entry.commitId,
                changeId = entry.changeId,
                authorName = entry.authorName,
                authorEmail = entry.authorEmail,
                date = entry.date,
            )
        }
        val annotatedContent = String(file.contentsToByteArray(), file.charset)
        val revisions = buildRevisionsList(repo, relative, file, lines)
        return JjFileAnnotation(project, file, annotatedContent, lines, revisions)
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
            JjFileRevision(
                repo = repo,
                filePath = filePath,
                relativePath = relative,
                commitId = line.commitId,
                changeId = line.changeId,
                author = JjJsonDecoders.formatAuthor(line.authorName, line.authorEmail),
                date = line.date,
                message = "",
            )
        }
    }

    private fun relativize(repo: JjRepository, absolutePath: String): String? {
        val rootPath = repo.rootPath
        if (absolutePath == rootPath) return ""
        val prefix = if (rootPath.endsWith('/')) rootPath else "$rootPath/"
        if (!absolutePath.startsWith(prefix)) return null
        return absolutePath.substring(prefix.length)
    }

    companion object {
        private val TEMPLATE =
            JjTemplates.annotationJsonLine {
                obj {
                    "commitId" to string(commitId)
                    "changeId" to string(changeId)
                    "authorName" to string(author.name())
                    "authorEmail" to string(author.email())
                    "timestamp" to string(author.timestamp())
                    "lineNumber" to num(lineNumber)
                }
            }
    }
}
