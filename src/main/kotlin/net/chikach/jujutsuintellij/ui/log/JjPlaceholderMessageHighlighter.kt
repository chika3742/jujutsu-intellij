package net.chikach.jujutsuintellij.ui.log

import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory

/** Renders the synthetic subjects `<no description set>` / `<root>` in italic. */
class JjPlaceholderMessageHighlighter : VcsLogHighlighter {

    override fun getStyle(
        commitId: Int,
        commitDetails: VcsShortCommitDetails,
        column: Int,
        isSelected: Boolean,
    ): VcsLogHighlighter.VcsCommitStyle {
        val subject = commitDetails.subject
        if (subject == JjLogProvider.NO_DESCRIPTION_PLACEHOLDER || subject == JjLogProvider.ROOT_PLACEHOLDER) {
            return VcsCommitStyleFactory.createStyle(null, null, VcsLogHighlighter.TextStyle.ITALIC)
        }
        return VcsLogHighlighter.VcsCommitStyle.DEFAULT
    }

    override fun update(dataPack: VcsLogDataPack, refreshHappened: Boolean) {}

    class Factory : VcsLogHighlighterFactory {
        override fun createHighlighter(logData: VcsLogData, logUi: VcsLogUi): VcsLogHighlighter =
            JjPlaceholderMessageHighlighter()

        override fun getId(): String = ID
        override fun getTitle(): String = "Jujutsu Placeholder Messages"
        override fun showMenuItem(): Boolean = false
    }

    companion object {
        const val ID = "JUJUTSU_PLACEHOLDER_MESSAGES"
    }
}
