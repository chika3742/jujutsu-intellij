package net.chikach.jujutsuintellij.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JjJsonParsingTest {

    @Test
    fun `parses newline-delimited objects`() {
        val objects = JjJsonParser.parseObjects(
            """
            {"commitId":"abc","changeId":"def","authorName":"Alice","authorEmail":"alice@example.com","timestamp":"2025-01-02 03:04:05 +00:00","description":"hello\n"}
            {"commitId":"ghi","changeId":"jkl","authorName":"","authorEmail":"bot@example.com","timestamp":"2025-01-02T03:04:05+00:00","description":""}
            """.trimIndent(),
            "jj log"
        )

        val entries = JjJsonDecoders.decodeHistoryEntries(objects)

        assertEquals(2, entries.size)
        assertEquals("abc", entries[0].commitId)
        assertEquals("Alice <alice@example.com>", entries[0].author)
        assertEquals("hello", entries[0].description)
        assertEquals("bot@example.com", entries[1].author)
    }

    @Test
    fun `rejects non object json lines`() {
        assertFailsWith<JjJsonException> {
            JjJsonParser.parseObjects("\"not-an-object\"", "jj log")
        }
    }
}
