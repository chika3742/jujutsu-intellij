package net.chikach.jujutsuintellij.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class JjCommandFactoryTest {

    @Test
    fun `builds version request with dedicated timeout`() {
        val request = JjCommandFactory.version(Path.of("/repo"))

        assertEquals(Path.of("/repo"), request.workDir)
        assertEquals(listOf("--version"), request.args)
        assertEquals(JjCommandFactory.VERSION_TIMEOUT_MS, request.timeoutMs)
    }

    @Test
    fun `builds file history request with fileset separator`() {
        val request = JjCommandFactory.fileHistory(
            workDir = Path.of("/repo"),
            revset = "::@",
            template = "template",
            relativePath = "src/Main.kt",
        )

        assertEquals(
            listOf("log", "--no-graph", "-r", "::@", "-T", "template", "--", "src/Main.kt"),
            request.args,
        )
    }

    @Test
    fun `builds annotate request with optional revision`() {
        val requestWithRevision = JjCommandFactory.annotateFile(
            workDir = Path.of("/repo"),
            template = "template",
            relativePath = "src/Main.kt",
            revision = "@-",
        )
        val requestWithoutRevision = JjCommandFactory.annotateFile(
            workDir = Path.of("/repo"),
            template = "template",
            relativePath = "src/Main.kt",
        )

        assertEquals(
            listOf("file", "annotate", "-T", "template", "-r", "@-", "src/Main.kt"),
            requestWithRevision.args,
        )
        assertEquals(
            listOf("file", "annotate", "-T", "template", "src/Main.kt"),
            requestWithoutRevision.args,
        )
    }
}
