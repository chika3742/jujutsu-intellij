package net.chikach.jujutsuintellij.ui.log

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory
import net.chikach.jujutsuintellij.caches.JjCommitCache
import net.chikach.jujutsuintellij.caches.JjWorkingCopyCache
import net.chikach.jujutsuintellij.vcs.JjConflictTracker
import java.awt.Color

/**
 * Combines jj-specific log styling:
 *  - the working-copy commit (`@`) renders in bold.
 *  - placeholder subjects (`<no description set>`, `<root>`) render in italic.
 *  - commits flagged as conflicted by [JjConflictTracker] render in red.
 */
class JjLogStyleHighlighter(private val project: Project) : VcsLogHighlighter {

    override fun getStyle(
        commitId: Int,
        commitDetails: VcsShortCommitDetails,
        column: Int,
        isSelected: Boolean,
    ): VcsLogHighlighter.VcsCommitStyle {
        val hash = commitDetails.id.asString()
        val isWorkingCopy = hash == JjWorkingCopyCache.getInstance(project).commitId
        val isConflicted = JjConflictTracker.getInstance(project).isConflicted(hash)
        val info = JjCommitCache.getInstance(project).get(hash)
        val isPlaceholder = info != null && (info.isRoot || info.description.isBlank())

        val fg: Color? = when {
            isConflicted -> CONFLICT_FG
            isWorkingCopy -> WORKING_COPY_FG
            else -> null
        }
        // A single text style applies, so bold (working copy) takes precedence over italic (placeholder).
        val textStyle = when {
            isWorkingCopy -> VcsLogHighlighter.TextStyle.BOLD
            isPlaceholder -> VcsLogHighlighter.TextStyle.ITALIC
            else -> VcsLogHighlighter.TextStyle.NORMAL
        }
        if (fg == null && textStyle == VcsLogHighlighter.TextStyle.NORMAL) {
            return VcsLogHighlighter.VcsCommitStyle.DEFAULT
        }
        return VcsCommitStyleFactory.createStyle(fg, null, textStyle)
    }

    override fun update(dataPack: VcsLogDataPack, refreshHappened: Boolean) {}

    class Factory : VcsLogHighlighterFactory {
        override fun createHighlighter(logData: VcsLogData, logUi: VcsLogUi): VcsLogHighlighter =
            JjLogStyleHighlighter(logData.project)

        override fun getId(): String = ID
        override fun getTitle(): String = "Jujutsu Log Styling"
        override fun showMenuItem(): Boolean = false
    }

    companion object {
        const val ID = "JUJUTSU_LOG_STYLE"
        private val CONFLICT_FG: JBColor = JBColor(Color(0xC7222E), Color(0xFF6B7A))
        private val WORKING_COPY_FG: JBColor = JBColor(Color(0x1A7F37), Color(0x3FB950))
    }
}
