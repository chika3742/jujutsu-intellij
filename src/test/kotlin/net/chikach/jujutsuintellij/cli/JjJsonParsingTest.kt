package net.chikach.jujutsuintellij.cli

import net.chikach.jujutsuintellij.repo.model.JjAnnotationLine
import net.chikach.jujutsuintellij.repo.model.JjCommit
import net.chikach.jujutsuintellij.repo.model.JjCommitRef
import net.chikach.jujutsuintellij.repo.model.JjHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JjJsonParsingTest {

    @Test
    fun `parses newline-delimited history entries`() {
        val entries = JjJsonParser.parseList<JjHistoryEntry>(
            """
            {"commitId":"abc","changeId":"def","authorName":"Alice","authorEmail":"alice@example.com","timestamp":"2025-01-02T03:04:05+00:00","description":"hello\n"}
            {"commitId":"ghi","changeId":"jkl","authorName":"","authorEmail":"bot@example.com","timestamp":"2025-01-02T03:04:05.123456+09:00","description":""}
            """.trimIndent(),
            "jj log",
        )

        assertEquals(2, entries.size)
        assertEquals("abc", entries[0].commitId)
        assertEquals("Alice <alice@example.com>", entries[0].author)
        assertEquals("hello\n", entries[0].description)
        assertEquals("bot@example.com", entries[1].author)
    }

    @Test
    fun `rejects malformed json lines`() {
        assertFailsWith<JjJsonException> {
            JjJsonParser.parseList<JjHistoryEntry>("\"not-an-object\"", "jj log")
        }
    }

    @Test
    fun `parses annotation entries`() {
        val entries = JjJsonParser.parseList<JjAnnotationLine>(
            """
            {"commitId":"abc","changeId":"chg1","authorName":"Alice","authorEmail":"alice@example.com","timestamp":"2025-01-02T03:04:05+00:00"}
            {"commitId":"def","changeId":"chg2","authorName":"","authorEmail":"bot@example.com","timestamp":"2025-01-02T03:04:05+00:00"}
            """.trimIndent(),
            "jj file annotate",
        )

        assertEquals(2, entries.size)
        assertEquals("abc", entries[0].commitId)
        assertEquals("Alice <alice@example.com>", entries[0].author)
        assertEquals("bot@example.com", entries[1].author)
    }

    @Test
    fun `parses bookmark refs with tracking status`() {
        val refs = JjJsonParser.parseList<JjCommitRef>(
            """
            {"name":"main","remote":null,"commitId":"abc","tracked":false}
            {"name":"main","remote":"origin","commitId":"abc","tracked":true}
            {"name":"feature","remote":"origin","commitId":"def","tracked":false}
            """.trimIndent(),
            "jj bookmark list",
        )

        assertEquals(3, refs.size)
        assertTrue(refs[0].isLocal)
        assertFalse(refs[0].tracked)
        assertTrue(refs[1].isTrackedRemote)
        assertFalse(refs[2].isLocal)
        assertFalse(refs[2].tracked)
    }

    @Test
    fun `parses commit tags and defaults to empty when absent`() {
        val commits = JjJsonParser.parseList<JjCommit>(
            """
            {"commitId":"abc","changeId":"def","parentIds":[],"authorName":"Alice","authorEmail":"alice@example.com","authorTime":"2025-01-02T03:04:05+00:00","description":"hi","bookmarks":[],"tags":["v1.0","v1.1"]}
            {"commitId":"ghi","changeId":"jkl","parentIds":[],"authorName":"Alice","authorEmail":"alice@example.com","authorTime":"2025-01-02T03:04:05+00:00","description":"hi","bookmarks":[]}
            """.trimIndent(),
            "jj log",
        )

        assertEquals(2, commits.size)
        assertEquals(listOf("v1.0", "v1.1"), commits[0].tags)
        assertEquals(emptyList(), commits[1].tags)
    }

    @Test
    fun `parses commit with tracked and untracked remote bookmarks`() {
        val commits = JjJsonParser.parseList<JjCommit>(
            """
            {"commitId":"abc","changeId":"def","parentIds":["p1"],"authorName":"Alice","authorEmail":"alice@example.com","authorTime":"2025-01-02T03:04:05+00:00","description":"hi","bookmarks":["main"],"untrackedRemoteBookmarks":["feature@origin"],"trackedRemoteBookmarks":["main@origin"]}
            """.trimIndent(),
            "jj log",
        )

        assertEquals(1, commits.size)
        assertEquals(listOf("feature@origin"), commits[0].untrackedRemoteBookmarks)
        assertEquals(listOf("main@origin"), commits[0].trackedRemoteBookmarks)
    }
}