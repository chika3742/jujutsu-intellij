package net.chikach.jujutsuintellij.cli

import net.chikach.jujutsuintellij.repo.model.JjAnnotationLine
import net.chikach.jujutsuintellij.repo.model.JjHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}