package net.chikach.jujutsuintellij.cli.template

import kotlin.test.Test
import kotlin.test.assertEquals

class JjTemplatesTest {

    @Test
    fun `renders commit json lines template`() {
        val template = JjTemplates.commitJsonLine {
            obj {
                "commitId" to string(commitId())
                "description" to string(description())
            }
        }

        assertEquals(
            "\"{\" ++ \"\\\"commitId\\\":\" ++ stringify(self.commit_id()).escape_json() ++ \",\" ++ " +
                "\"\\\"description\\\":\" ++ stringify(self.description()).escape_json() ++ \"}\" ++ \"\\n\"",
            template
        )
    }

    @Test
    fun `renders commit ref tracked status`() {
        val template = JjTemplates.commitRefJsonLine {
            obj {
                "name" to string(name())
                "tracked" to bool(tracked())
            }
        }

        assertEquals(
            "\"{\" ++ \"\\\"name\\\":\" ++ stringify(self.name()).escape_json() ++ \",\" ++ " +
                "\"\\\"tracked\\\":\" ++ self.tracked() ++ \"}\" ++ \"\\n\"",
            template
        )
    }

    @Test
    fun `renders local tag names`() {
        val template = JjTemplates.commitJsonLine {
            obj {
                "tags" to serialized(localTags())
            }
        }

        assertEquals(
            "\"{\" ++ \"\\\"tags\\\":\" ++ json(self.local_tags().map(|p| p.name())) ++ \"}\" ++ \"\\n\"",
            template
        )
    }

    @Test
    fun `renders untracked remote bookmark labels`() {
        val template = JjTemplates.commitJsonLine {
            obj {
                "untrackedRemoteBookmarks" to serialized(untrackedRemoteBookmarkLabels())
            }
        }

        assertEquals(
            "\"{\" ++ \"\\\"untrackedRemoteBookmarks\\\":\" ++ " +
                "json(self.remote_bookmarks().filter(|p| !p.tracked() && p.remote() != \"git\")" +
                ".map(|p| stringify(p.name() ++ \"@\" ++ p.remote()))) ++ \"}\" ++ \"\\n\"",
            template
        )
    }

    @Test
    fun `renders tracked remote bookmark labels`() {
        val template = JjTemplates.commitJsonLine {
            obj {
                "trackedRemoteBookmarks" to serialized(trackedRemoteBookmarkLabels())
            }
        }

        assertEquals(
            "\"{\" ++ \"\\\"trackedRemoteBookmarks\\\":\" ++ " +
                "json(self.remote_bookmarks().filter(|p| p.tracked() && p.remote() != \"git\")" +
                ".map(|p| stringify(p.name() ++ \"@\" ++ p.remote()))) ++ \"}\" ++ \"\\n\"",
            template
        )
    }
}
