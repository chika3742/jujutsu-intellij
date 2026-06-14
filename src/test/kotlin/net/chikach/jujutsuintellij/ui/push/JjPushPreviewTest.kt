package net.chikach.jujutsuintellij.ui.push

import net.chikach.jujutsuintellij.repo.model.JjCommitRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the pure push-preview logic ([planPushTargets], [classifyMove]) without invoking `jj`.
 * The commit-list / revset side of [computeChanges] is covered by manual verification (runIde).
 */
class JjPushPreviewTest {

    private fun local(name: String, commitId: String?) = JjCommitRef(name, remote = null, commitId = commitId)
    private fun remote(name: String, remote: String, commitId: String?, tracked: Boolean) =
        JjCommitRef(name, remote = remote, commitId = commitId, tracked = tracked)

    @Test
    fun `up-to-date bookmark is skipped`() {
        val refs = listOf(local("main", "abc"), remote("main", "origin", "abc", tracked = true))
        assertEquals(emptyList(), planPushTargets(refs, "origin"))
    }

    @Test
    fun `diverged tracked bookmark becomes an update target`() {
        val refs = listOf(local("main", "def"), remote("main", "origin", "abc", tracked = true))
        assertEquals(
            listOf(PushTarget("main", localId = "def", remoteId = "abc")),
            planPushTargets(refs, "origin"),
        )
    }

    @Test
    fun `new bookmark is included as an add target`() {
        val refs = listOf(local("feature", "x"))
        assertEquals(
            listOf(PushTarget("feature", localId = "x", remoteId = null)),
            planPushTargets(refs, "origin"),
        )
    }

    @Test
    fun `untracked remote ref is treated as a new bookmark`() {
        val refs = listOf(local("feature", "x"), remote("feature", "origin", "y", tracked = false))
        assertEquals(
            listOf(PushTarget("feature", localId = "x", remoteId = null)),
            planPushTargets(refs, "origin"),
        )
    }

    @Test
    fun `tracked remote ref without a local bookmark becomes a delete target`() {
        val refs = listOf(remote("old", "origin", "abc", tracked = true))
        assertEquals(
            listOf(PushTarget("old", localId = null, remoteId = "abc")),
            planPushTargets(refs, "origin"),
        )
    }

    @Test
    fun `conflicted local bookmark is skipped`() {
        val refs = listOf(local("main", null), remote("main", "origin", "abc", tracked = true))
        assertEquals(emptyList(), planPushTargets(refs, "origin"))
    }

    @Test
    fun `tracked ref on a different remote is ignored for the selected remote`() {
        val refs = listOf(local("main", "def"), remote("main", "upstream", "def", tracked = true))
        // No tracked counterpart on origin, so it is a new bookmark on origin.
        assertEquals(
            listOf(PushTarget("main", localId = "def", remoteId = null)),
            planPushTargets(refs, "origin"),
        )
    }

    @Test
    fun `classifyMove distinguishes forward sideways and backward`() {
        assertEquals(BookmarkPushAction.MOVE_FORWARD, classifyMove(aheadEmpty = false, behindEmpty = true))
        assertEquals(BookmarkPushAction.MOVE_SIDEWAYS, classifyMove(aheadEmpty = false, behindEmpty = false))
        assertEquals(BookmarkPushAction.MOVE_BACKWARD, classifyMove(aheadEmpty = true, behindEmpty = false))
    }
}
