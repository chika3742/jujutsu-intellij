package net.chikach.jujutsuintellij.ui.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory
import net.chikach.jujutsuintellij.caches.JjCommitCache

/** Renders the synthetic subjects `<no description set>` / `<root>` in italic. */
class JjPlaceholderMessageHighlighter(private val project: Project) : VcsLogHighlighter {

    override fun getStyle(
        commitId: Int,
        commitDetails: VcsShortCommitDetails,
        column: Int,
        isSelected: Boolean,
    ): VcsLogHighlighter.VcsCommitStyle {
        val commitInfo = JjCommitCache.getInstance(project).get(commitDetails.id.asString())
            ?: return VcsLogHighlighter.VcsCommitStyle.DEFAULT
        if (commitInfo.description.isEmpty() || commitInfo.isRoot) {
            return VcsCommitStyleFactory.createStyle(null, null, VcsLogHighlighter.TextStyle.ITALIC)
        }
        return VcsLogHighlighter.VcsCommitStyle.DEFAULT
    }

    override fun update(dataPack: VcsLogDataPack, refreshHappened: Boolean) {}

    class Factory : VcsLogHighlighterFactory {
        override fun createHighlighter(logData: VcsLogData, logUi: VcsLogUi): VcsLogHighlighter =
            JjPlaceholderMessageHighlighter(logData.project)

        override fun getId(): String = ID
        override fun getTitle(): String = "Jujutsu Placeholder Messages"
        override fun showMenuItem(): Boolean = false
    }

    companion object {
        const val ID = "JUJUTSU_PLACEHOLDER_MESSAGES"
    }
}
