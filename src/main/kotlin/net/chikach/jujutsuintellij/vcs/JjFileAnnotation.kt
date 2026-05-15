package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.DateFormatUtil
import net.chikach.jujutsuintellij.repo.model.JjAnnotationLine
import java.util.*

class JjFileAnnotation(
    project: Project,
    private val file: VirtualFile,
    private val content: String,
    private val lines: List<JjAnnotationLine>,
    private val revisions: List<VcsFileRevision>,
) : FileAnnotation(project) {

    private val aspects: Array<LineAnnotationAspect> = arrayOf(
        RevisionAspect(),
        DateAspect(),
        AuthorAspect(),
    )

    override fun getFile(): VirtualFile = file

    override fun getAnnotatedContent(): String = content

    override fun getCurrentRevision(): VcsRevisionNumber? = null

    override fun getAspects(): Array<LineAnnotationAspect> = aspects

    override fun getLineCount(): Int = lines.size

    override fun getLineRevisionNumber(lineNumber: Int): VcsRevisionNumber? =
        lines.getOrNull(lineNumber)?.let { net.chikach.jujutsuintellij.model.JjRevisionNumber(it.commitId) }

    override fun getLineDate(lineNumber: Int): Date? = lines.getOrNull(lineNumber)?.date

    override fun getToolTip(lineNumber: Int): String? {
        val line = lines.getOrNull(lineNumber) ?: return null
        val author = when {
            line.authorName.isNotBlank() && line.authorEmail.isNotBlank() -> "${line.authorName} <${line.authorEmail}>"
            line.authorName.isNotBlank() -> line.authorName
            else -> line.authorEmail
        }
        return "${line.commitId.take(12)}\n$author\n${DateFormatUtil.formatDateTime(line.date)}"
    }

    override fun getRevisions(): List<VcsFileRevision> = revisions

    override fun getVcsKey() = JujutsuVcs.KEY

    private inner class RevisionAspect :
        LineAnnotationAspectAdapter(REVISION, REVISION, false) {
        override fun getValue(line: Int): String = lines.getOrNull(line)?.commitId?.take(8) ?: ""
        override fun getTooltipText(lineNumber: Int): String? = getToolTip(lineNumber)
        override fun showAffectedPaths(lineNum: Int) {}
    }

    private inner class DateAspect :
        LineAnnotationAspectAdapter(DATE, DATE, true) {
        override fun getValue(line: Int): String =
            lines.getOrNull(line)?.let { DateFormatUtil.formatPrettyDate(it.date) } ?: ""
        override fun getTooltipText(lineNumber: Int): String? = getToolTip(lineNumber)
        override fun showAffectedPaths(lineNum: Int) {}
    }

    private inner class AuthorAspect :
        LineAnnotationAspectAdapter(AUTHOR, AUTHOR, true) {
        override fun getValue(line: Int): String = lines.getOrNull(line)?.authorName ?: ""
        override fun getTooltipText(lineNumber: Int): String? = getToolTip(lineNumber)
        override fun showAffectedPaths(lineNum: Int) {}
    }
}
