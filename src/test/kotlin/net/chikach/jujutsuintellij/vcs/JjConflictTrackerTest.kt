package net.chikach.jujutsuintellij.vcs

import net.chikach.jujutsuintellij.repo.model.JjCommit
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JjConflictTrackerTest {

    @Test
    fun `records conflicted commits and removes resolved ones`() {
        val tracker = JjConflictTracker()

        tracker.record(listOf(commit("aaa", conflictedFiles = listOf("f.txt"))))
        assertTrue(tracker.isConflicted("aaa"))

        tracker.record(listOf(commit("aaa", conflictedFiles = emptyList())))
        assertFalse(tracker.isConflicted("aaa"))
    }

    @Test
    fun `unknown hashes are not conflicted`() {
        val tracker = JjConflictTracker()
        assertFalse(tracker.isConflicted("missing"))
    }

    private fun commit(
        commitId: String,
        conflictedFiles: List<String>,
    ): JjCommit = JjCommit(
        commitId = commitId,
        changeId = "change-$commitId",
        parentIds = emptyList(),
        authorName = "tester",
        authorEmail = "t@example.com",
        authorTime = Date(0),
        description = "",
        bookmarks = emptyList(),
        isRoot = false,
        conflictedFiles = conflictedFiles,
    )
}
