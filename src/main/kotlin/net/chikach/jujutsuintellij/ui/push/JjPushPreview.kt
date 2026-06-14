package net.chikach.jujutsuintellij.ui.push

import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.model.JjCommit
import net.chikach.jujutsuintellij.repo.model.JjCommitRef

/**
 * Read-only preview of what a `jj git push --remote <remote>` would do, computed from cached
 * bookmark refs (local + remote targets) rather than `jj git push --dry-run`.
 *
 * The set of affected bookmarks comes purely from comparing local and tracked-remote targets in the
 * cached [JjCommitRef] list — the single source of truth kept fresh by
 * [net.chikach.jujutsuintellij.repo.JjChangeWatcher] (see [planPushTargets], which is pure). The
 * pushed-commit list and the fine-grained move classification come from live revset reads, so
 * [computeChanges] must run off the EDT.
 */

enum class BookmarkPushAction { ADD, MOVE_FORWARD, MOVE_SIDEWAYS, MOVE_BACKWARD, DELETE }

/**
 * One bookmark that would change on the remote.
 *
 * - [localId] is the new target; `null` only for [BookmarkPushAction.DELETE].
 * - [remoteId] is the current target on the remote; `null` only for [BookmarkPushAction.ADD].
 * - [aheadCommits] are the commits that would newly appear on the remote (`<remoteId>..<localId>`,
 *   or `remote_bookmarks(remote=exact:R)..<localId>` for an add). Empty for a delete.
 */
data class BookmarkPushChange(
    val name: String,
    val action: BookmarkPushAction,
    val localId: String?,
    val remoteId: String?,
    val aheadCommits: List<JjCommit>,
)

/**
 * A bookmark selected for push by target comparison, before its commits / move direction are
 * resolved. [localId] is `null` for a deletion; [remoteId] is `null` for a new bookmark.
 */
internal data class PushTarget(val name: String, val localId: String?, val remoteId: String?)

/**
 * Pure selection of the bookmarks that pushing to [remote] would affect, from the cached [refs].
 *
 * - A local bookmark whose tracked-remote target on [remote] differs from its local target → update.
 * - A local bookmark with no tracked counterpart on [remote] → add (jj tracks it automatically).
 * - A tracked remote ref on [remote] with no local bookmark → delete (always propagated).
 *
 * Conflicted local bookmarks (no `commitId`) and up-to-date bookmarks are skipped.
 */
internal fun planPushTargets(
    refs: List<JjCommitRef>,
    remote: String,
): List<PushTarget> {
    val targets = mutableListOf<PushTarget>()
    for ((name, group) in refs.groupBy { it.name }) {
        val local = group.firstOrNull { it.isLocal }
        val trackedRemote = group.firstOrNull { it.remote == remote && it.tracked }

        if (local == null) {
            if (trackedRemote != null) targets += PushTarget(name, null, trackedRemote.commitId)
            continue
        }

        val localId = local.commitId ?: continue // conflicted bookmark: cannot be pushed
        val remoteId = trackedRemote?.commitId
        if (remoteId == localId) continue // already up to date
        targets += PushTarget(name, localId, remoteId)
    }
    return targets.sortedBy { it.name }
}

/** Pure move classification from whether the new target is ahead of / behind the remote target. */
internal fun classifyMove(aheadEmpty: Boolean, behindEmpty: Boolean): BookmarkPushAction = when {
    !behindEmpty && !aheadEmpty -> BookmarkPushAction.MOVE_SIDEWAYS
    !behindEmpty -> BookmarkPushAction.MOVE_BACKWARD
    else -> BookmarkPushAction.MOVE_FORWARD
}

/**
 * Computes the bookmark changes that pushing [repo] to [remote] would produce, using the cached
 * [refs] for target comparison ([planPushTargets]) and live revsets for commit lists / direction.
 * Runs `jj` synchronously; call from a background thread.
 */
fun computeChanges(
    repo: JjRepository,
    refs: List<JjCommitRef>,
    remote: String,
): List<BookmarkPushChange> =
    planPushTargets(refs, remote).map { target -> resolveChange(repo, remote, target) }

private fun resolveChange(repo: JjRepository, remote: String, target: PushTarget): BookmarkPushChange {
    val (name, localId, remoteId) = target
    if (localId == null) {
        return BookmarkPushChange(name, BookmarkPushAction.DELETE, null, remoteId, emptyList())
    }
    if (remoteId == null) {
        val ahead = repo.logByRevset("remote_bookmarks(remote=exact:$remote)..$localId")
        return BookmarkPushChange(name, BookmarkPushAction.ADD, localId, null, ahead)
    }
    val ahead = repo.logByRevset("$remoteId..$localId")
    val behindEmpty = repo.logByRevset("$localId..$remoteId").isEmpty()
    return BookmarkPushChange(name, classifyMove(ahead.isEmpty(), behindEmpty), localId, remoteId, ahead)
}

/** Names of the bookmarks that would actually be pushed (everything in [changes]). */
fun pushedBookmarkNames(changes: List<BookmarkPushChange>): List<String> = changes.map { it.name }

/** Local tag names reachable from the targets of the pushed bookmarks (excludes deletions). */
fun tagsOnPushedBookmarks(repo: JjRepository, changes: List<BookmarkPushChange>): List<String> {
    val targets = changes.filter { it.action != BookmarkPushAction.DELETE }.mapNotNull { it.localId }
    if (targets.isEmpty()) return emptyList()
    val tagged = repo.logByRevset("::(${targets.joinToString("|")}) & tags()")
    return tagged.flatMap { it.tags }.distinct()
}

/** All local tag names in [repo]. */
fun allLocalTags(repo: JjRepository): List<String> =
    repo.listTags().filter { it.isLocal }.map { it.name }.distinct()
