package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.model.JjAnnotationLine

/** Drives IntelliJ's "Annotate with Git Blame" equivalent for jj. */
@Service(Service.Level.PROJECT)
class JjAnnotationProvider(private val project: Project) : AnnotationProvider {

    @Throws(VcsException::class)
    override fun annotate(file: VirtualFile): FileAnnotation = annotate(file, null)

    @Throws(VcsException::class)
    override fun annotate(file: VirtualFile, revision: VcsFileRevision?): FileAnnotation {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForFile(file)
            ?: throw VcsException("File is not under a Jujutsu repository: ${file.path}")
        val relative = repo.relativize(file.path)
            ?: throw VcsException("Cannot compute path for ${file.path} inside ${repo.rootPath}")

        val lines = try {
            repo.annotateFile(relative, revision?.revisionNumber?.asString())
        } catch (e: Exception) {
            throw VcsException("Failed to run jj file annotate on $relative: ${e.message}", e)
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
                author = line.author,
                date = line.date,
                message = "",
            )
        }
    }
}
