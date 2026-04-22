package net.chikach.jujutsuintellij.cli.template

import kotlin.test.Test
import kotlin.test.assertEquals

class JjTemplatesTest {

    @Test
    fun `renders commit json lines template`() {
        val template = JjTemplates.commitJsonLine {
            obj {
                "commitId" to string(commitId)
                "description" to string(description)
            }
        }

        assertEquals(
            "\"{\" ++ \"\\\"commitId\\\":\" ++ stringify(self.commit_id()).escape_json() ++ \",\" ++ " +
                "\"\\\"description\\\":\" ++ stringify(self.description()).escape_json() ++ \"}\" ++ \"\\n\"",
            template
        )
    }

    @Test
    fun `renders annotation json lines template with numeric field`() {
        val template = JjTemplates.annotationJsonLine {
            obj {
                "commitId" to string(commitId)
                "lineNumber" to num(lineNumber)
            }
        }

        assertEquals(
            "\"{\" ++ \"\\\"commitId\\\":\" ++ stringify(self.commit().commit_id()).escape_json() ++ \",\" ++ " +
                "\"\\\"lineNumber\\\":\" ++ self.line_number() ++ \"}\" ++ \"\\n\"",
            template
        )
    }
}
