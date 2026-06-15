package net.chikach.jujutsuintellij.ui.push

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.chikach.jujutsuintellij.repo.model.JjCommit
import javax.swing.JEditorPane

/**
 * Read-only HTML panel that renders a [JjCommit]'s subject, body, short id, bookmarks/tags,
 * author and date. Built only from public IntelliJ APIs to avoid the `@ApiStatus.Internal`
 * commit-details components in `com.intellij.vcs.log.ui.*`.
 *
 * Swing's `HTMLEditorKit` supports only a limited subset of CSS, so the renderer sticks to
 * tags (`<b>`, `<font color>`) and basic block-level styles (`margin`, `color`,
 * `background-color`). Rounded corners and other modern CSS are intentionally avoided.
 */
class JjPushCommitDetailsPanel : JEditorPane() {

    init {
        contentType = "text/html"
        editorKit = HTMLEditorKitBuilder.simple()
        isEditable = false
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(8, 12)
        setCommit(null)
    }

    fun setCommit(commit: JjCommit?) {
        text = if (commit == null) "" else renderCommit(commit, DateFormatUtil.formatPrettyDateTime(commit.authorTime))
        caretPosition = 0
    }

    internal companion object {
        const val SHORT_HASH_LEN = 8

        val MUTED: JBColor = JBColor(0x6E6E6E, 0xA8A8A8)
        val BOOKMARK_BG: JBColor = JBColor(0xDDEFFF, 0x274769)
        val BOOKMARK_FG: JBColor = JBColor(0x0A4F86, 0xCCE5FF)
        val TAG_BG: JBColor = JBColor(0xFFF1CC, 0x5C4A1A)
        val TAG_FG: JBColor = JBColor(0x80591A, 0xFFE8A8)

        fun color(c: JBColor): String = "#" + ColorUtil.toHex(c)

        /**
         * Pure rendering function. Exposed for headless testing — kept on the companion so
         * tests need no Swing component instantiation.
         */
        internal fun renderCommit(commit: JjCommit, dateText: String): String {
            val subject = commit.description.lineSequence().firstOrNull().orEmpty()
                .ifBlank { "(no description)" }
            val body = commit.description.lineSequence().drop(1).joinToString("\n").trim()
            val authorDisplay = if (commit.authorEmail.isBlank()) commit.authorName
            else "${commit.authorName} <${commit.authorEmail}>"

            val builder = HtmlBuilder()

            // Header row: short hash, then bookmarks/tags as labels.
            val headerChildren = mutableListOf<HtmlChunk>(
                HtmlChunk.tag("code")
                    .style("color: ${color(MUTED)};")
                    .addText(commit.commitId.take(SHORT_HASH_LEN))
            )
            for (name in commit.bookmarks) {
                headerChildren += HtmlChunk.nbsp(2)
                headerChildren += label(name, BOOKMARK_BG, BOOKMARK_FG)
            }
            for (name in commit.tags) {
                headerChildren += HtmlChunk.nbsp(2)
                headerChildren += label(name, TAG_BG, TAG_FG)
            }
            builder.append(HtmlChunk.div().style("margin-bottom: 6px;").children(headerChildren))

            // Subject — bold via <b> tag (CSS font-weight is unreliable in Swing).
            builder.append(
                HtmlChunk.div().style("margin-bottom: 6px;")
                    .child(HtmlChunk.text(subject).bold())
            )

            // Body — HtmlChunk.text turns '\n' into <br/>, preserving the original line breaks.
            if (body.isNotEmpty()) {
                builder.append(HtmlChunk.div().style("margin-bottom: 10px;").addText(body))
            }

            // Footer: author and date in muted color.
            builder.append(
                HtmlChunk.div().style("color: ${color(MUTED)};")
                    .children(
                        HtmlChunk.text(authorDisplay),
                        HtmlChunk.nbsp(),
                        HtmlChunk.text("·"),
                        HtmlChunk.nbsp(),
                        HtmlChunk.text(dateText),
                    )
            )

            return builder.wrapWithHtmlBody().toString()
        }

        private fun label(text: String, bg: JBColor, fg: JBColor): HtmlChunk =
            HtmlChunk.span()
                .style("background-color: ${color(bg)}; color: ${color(fg)};")
                .addText(" $text ")
    }
}
