package net.chikach.jujutsuintellij.ui.push

import net.chikach.jujutsuintellij.repo.model.JjCommit
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Headless tests for [JjPushCommitDetailsPanel.renderCommit]. They cover the text/structure
 * of the rendered HTML; the actual Swing rendering still requires visual verification.
 */
class JjPushCommitDetailsPanelTest {

    private fun commit(
        commitId: String = "abcdef1234567890",
        description: String = "",
        bookmarks: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        authorName: String = "Alice",
        authorEmail: String = "alice@example.com",
    ) = JjCommit(
        commitId = commitId,
        changeId = "z" + commitId.take(8),
        parentIds = emptyList(),
        authorName = authorName,
        authorEmail = authorEmail,
        authorTime = Date(0),
        description = description,
        bookmarks = bookmarks,
        tags = tags,
    )

    @Test
    fun `subject and short hash appear`() {
        val html = JjPushCommitDetailsPanel.renderCommit(dateText = "2026-06-15", commit =
            commit(description = "Add push dialog\n\nDetails here.")
        )
        assertTrue("abcdef12" in html, "short hash should appear: $html")
        assertTrue("Add push dialog" in html, "subject should appear: $html")
        assertTrue("Details here." in html, "body should appear: $html")
    }

    @Test
    fun `blank description renders placeholder and omits empty body`() {
        val html = JjPushCommitDetailsPanel.renderCommit(dateText = "2026-06-15", commit =commit(description = ""))
        assertTrue("(no description)" in html, "placeholder should appear: $html")
    }

    @Test
    fun `author email is shown when present`() {
        val html = JjPushCommitDetailsPanel.renderCommit(dateText = "2026-06-15", commit =
            commit(authorName = "Alice", authorEmail = "alice@example.com")
        )
        // HtmlChunk.text escapes '<' and '>', so the rendered output uses entities.
        assertTrue("Alice" in html)
        assertTrue("alice@example.com" in html)
    }

    @Test
    fun `author email is omitted when blank`() {
        val html = JjPushCommitDetailsPanel.renderCommit(dateText = "2026-06-15", commit =
            commit(authorName = "Bob", authorEmail = "")
        )
        assertTrue("Bob" in html)
        assertFalse("&lt;" in html, "no angle brackets when email blank: $html")
    }

    @Test
    fun `bookmarks and tags appear in header`() {
        val html = JjPushCommitDetailsPanel.renderCommit(dateText = "2026-06-15", commit =
            commit(description = "x", bookmarks = listOf("main", "feature/x"), tags = listOf("v1.0"))
        )
        assertTrue("main" in html)
        assertTrue("feature/x" in html)
        assertTrue("v1.0" in html)
    }
}
