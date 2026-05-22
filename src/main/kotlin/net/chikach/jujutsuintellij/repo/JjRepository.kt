package net.chikach.jujutsuintellij.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.jujutsuintellij.cli.JjCommandResult
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.repo.model.*
import java.io.File
import java.nio.file.Path

/**
 * In-memory handle for a single Jujutsu working copy rooted at [root].
 *
 * The class exposes use-case oriented operations (commit, log, diff, annotate, bookmarks,
 * git interop). Each operation translates results from [JjCommands] into domain objects defined
 * in [net.chikach.jujutsuintellij.repo.model]. Mutation operations throw [JjOperationException]
 * on CLI failure; reads return decoded objects or `null` where appropriate.
 *
 * Methods perform synchronous CLI calls and must not be invoked from the EDT.
 */
class JjRepository(
    val project: Project,
    val root: VirtualFile,
) {
    val rootPath: String get() = root.path
    val rootPathNio: Path get() = Path.of(rootPath)

    fun normalizeRelativePath(relativePath: String): String =
        JjPathUtil.normalizeRelativePath(relativePath)

    fun relativize(absolutePath: String): String? =
        JjPathUtil.relativize(rootPath, absolutePath)

    fun containsPath(absolutePath: String): Boolean =
        JjPathUtil.isUnderRoot(rootPath, absolutePath)

    fun resolveRelativePath(relativePath: String): File =
        File(rootPath, normalizeRelativePath(relativePath))

    // ─── Working Copy ───────────────────────────────────────────────────────

    /**
     * Returns the description of the working-copy commit (`@`).
     * Returns an empty string when the description is unset or the CLI invocation fails.
     */
    fun workingCopyDescription(): String {
        val result = commands().getDescription(this)
        return if (result.isSuccess) result.stdout.trimEnd() else ""
    }

    /** Updates the description of [revision] (default `@`). */
    fun describe(message: String, revision: String? = null) {
        commands().describe(this, message, revision).orThrow("describe")
    }

    /** Moves the changes of [from] into [into] (`jj squash --from … --into …`). */
    fun squash(from: String, into: String) {
        commands().squash(this, from, into).orThrow("squash")
    }

    /** Rebases [revisions] onto [destination]. [mode] selects whether descendants move too. */
    fun rebase(revisions: String, destination: String, mode: RebaseMode) {
        commands().rebase(this, mode.flag, revisions, destination).orThrow("rebase")
    }

    fun newChange(revision: String? = null) {
        commands().new(this, revision).orThrow("new")
    }

    /** Sets the description on `@` and creates a new working-copy on top. */
    fun commit(message: String) {
        commands().commit(this, message).orThrow("commit")
    }

    fun abandon(revset: String = WORKING_COPY_REF) {
        commands().abandon(this, revset).orThrow("abandon")
    }

    /** Sets [revision] as the working-copy commit (`jj edit`). */
    fun edit(revision: String) {
        commands().edit(this, revision).orThrow("edit")
    }

    /** Reverts [relativePaths] in `@` to their parent state (`jj restore <paths>`). */
    fun restore(relativePaths: List<String>) {
        val normalized = relativePaths.map { normalizeRelativePath(it) }
        commands().restore(this, normalized).orThrow("restore")
    }

    // ─── Diff / Local Changes ───────────────────────────────────────────────

    /** Changes between `@-` (parent) and `@` (working-copy). */
    fun workingCopyChanges(): List<JjFileChange> = diffSummary(WORKING_COPY_FIRST_PARENT_REVSET, WORKING_COPY_REF)

    fun diffSummary(fromRef: String, toRef: String): List<JjFileChange> {
        val result = commands().diffSummary(this, fromRef, toRef)
        if (!result.isSuccess) throw JjOperationException.from("diff --summary", result)
        return parseDiffSummary(result.stdout)
    }

    // ─── File ───────────────────────────────────────────────────────────────

    fun fileHistory(relativePath: String, revset: String = ANCESTORS_OF_WORKING_COPY_REF): List<JjHistoryEntry> {
        val rel = normalizeRelativePath(relativePath)
        return commands().fileHistory(this, rel, revset)
            .map { it.copy(description = it.description.trimEnd('\n')) }
    }

    fun annotateFile(relativePath: String, revision: String? = null): List<JjAnnotationLine> {
        val rel = normalizeRelativePath(relativePath)
        return commands().annotateFile(this, rel, revision)
    }

    /** Returns the text content of [relativePath] at [revision]. */
    fun showFile(revision: String, relativePath: String): String {
        val rel = normalizeRelativePath(relativePath)
        val result = commands().showFile(this, revision, rel)
        if (!result.isSuccess) throw JjOperationException.from("file show", result)
        return result.stdout
    }

    // ─── Log ────────────────────────────────────────────────────────────────

    fun recentLog(count: Int): List<JjCommit> =
        commands().log(this, "latest(all(), $count)")

    fun allTimedCommits(): List<JjCommit> =
        commands().log(this, "all()")

    fun logByIds(commitIds: List<String>): List<JjCommit> {
        if (commitIds.isEmpty()) return emptyList()
        return commands().log(this, commitIds.joinToString("|"))
    }

    fun logByRevset(revset: String): List<JjCommit> = commands().log(this, revset)

    /** The working-copy commit (`@`), or `null` if it cannot be read. */
    fun workingCopyCommit(): JjCommit? = logByRevset(WORKING_COPY_REF).firstOrNull()

    /** Repo-relative paths of files that are in a conflicted state at `@`. */
    fun workingCopyConflictedFiles(): List<String> = workingCopyCommit()?.conflictedFiles.orEmpty()

    // ─── Bookmarks ──────────────────────────────────────────────────────────

    /**
     * Returns all bookmark / tag refs as one [JjCommitRef] per (name, remote?) pair.
     */
    fun listBookmarks(revset: String? = null): List<JjCommitRef> =
        commands().bookmarkList(this, revset)

    fun listBookmarksByCommitId(commitId: String): List<JjCommitRef> =
        listBookmarks("descendants(${commitId})")

    /**
     * Name of the nearest ancestor bookmark of `@` (the bookmark the working copy is built on top
     * of), or `null` when no ancestor is bookmarked. `heads(::@ & bookmarks())` selects the
     * bookmarked ancestors closest to `@`.
     */
    fun currentBranch(): String? =
        listBookmarks(revset = "heads(::@ & bookmarks())").firstOrNull()?.name

    fun createBookmark(name: String, revision: String = WORKING_COPY_REF) {
        commands().bookmarkCreate(this, name, revision).orThrow("bookmark create")
    }

    fun deleteBookmark(name: String) {
        commands().bookmarkDelete(this, name).orThrow("bookmark delete")
    }

    /**
     * Creates or moves [name] to [revision] (`jj bookmark set`). Set [allowBackwards] to permit
     * moving a bookmark backwards or sideways (jj refuses this by default).
     */
    fun setBookmark(name: String, revision: String, allowBackwards: Boolean = false) {
        commands().bookmarkSet(this, name, revision, allowBackwards).orThrow("bookmark set")
    }

    fun renameBookmark(oldName: String, newName: String) {
        commands().bookmarkRename(this, oldName, newName).orThrow("bookmark rename")
    }

    /** Drops [name] locally without recording a deletion to push (`jj bookmark forget`). */
    fun forgetBookmark(name: String) {
        commands().bookmarkForget(this, name).orThrow("bookmark forget")
    }

    fun trackBookmark(name: String, remote: String) {
        commands().bookmarkTrack(this, name, remote).orThrow("bookmark track")
    }

    fun untrackBookmark(name: String, remote: String) {
        commands().bookmarkUntrack(this, name, remote).orThrow("bookmark untrack")
    }

    // ─── Config ─────────────────────────────────────────────────────────────

    /** Returns the config value at [key], or `null` if unset / not retrievable. */
    fun configGet(key: String): String? {
        val result = commands().configGet(this, key)
        return if (result.isSuccess) result.stdout.trim().ifEmpty { null } else null
    }

    /** Returns `user.name`/`user.email`, or `null` if `user.name` is unset. */
    fun currentUser(): JjUser? {
        val name = configGet("user.name") ?: return null
        val email = configGet("user.email") ?: ""
        return JjUser(name, email)
    }

    // ─── Git coexistence ────────────────────────────────────────────────────

    fun gitFetch(remote: String? = null) {
        commands().gitFetch(this, remote).orThrow("git fetch")
    }

    fun gitPush(bookmark: String? = null, remote: String? = null) {
        commands().gitPush(this, bookmark, remote).orThrow("git push")
    }

    /** `jj rebase` revision-selection mode: `-r` moves only the revisions, `-s` adds descendants. */
    enum class RebaseMode(val flag: String) { REVISION("-r"), DESCENDANTS("-s") }

    override fun toString(): String = "JjRepository(root=$rootPath)"

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun commands(): JjCommands = JjCommands.getInstance()

    private fun JjCommandResult.orThrow(operation: String) {
        if (!isSuccess) throw JjOperationException.from(operation, this)
    }

    companion object {
        /**
         * Working-copy parent revset.
         * `first_parent(@)` rather than `@-`: the latter errors with "resolved to more than one
         * revision" when `@` is a merge commit. first_parent picks the first parent for merges and
         * behaves like `@-` for single-parent commits.
         */
        const val WORKING_COPY_FIRST_PARENT_REVSET = "first_parent(@)"

        /** `first_parent(<rev>)`: the parent revset that is unambiguous even for merge commits. */
        fun firstParentRef(revision: String): String = "first_parent($revision)"

        private const val WORKING_COPY_REF = "@"
        private const val ANCESTORS_OF_WORKING_COPY_REF = "::@"
    }
}

// ─── Internal parsers ───────────────────────────────────────────────────────

private const val RENAME_ARROW = " => "
private val BRACE_RENAME_REGEX = Regex("""^(.*?)\{(.*?) => (.*?)\}$""")

private fun parseDiffSummary(stdout: String): List<JjFileChange> {
    val out = mutableListOf<JjFileChange>()
    for (rawLine in stdout.lineSequence()) {
        val line = rawLine.removeSuffix("\r")
        if (line.length < 3) continue
        val statusChar = line[0]
        val body = line.substring(2)
        when (statusChar) {
            'A' -> out += JjFileChange(JjFileChange.Status.ADDED, body)
            'M' -> out += JjFileChange(JjFileChange.Status.MODIFIED, body)
            'D' -> out += JjFileChange(JjFileChange.Status.DELETED, body)
            'R', 'C' -> {
                val (old, new) = parseRenameBody(body) ?: continue
                val status = if (statusChar == 'R') JjFileChange.Status.RENAMED else JjFileChange.Status.COPIED
                out += JjFileChange(status, new, sourcePath = old)
            }
        }
    }
    return out
}

/**
 * jj renders renames/copies as one of:
 *   `prefix/{old => new}`, `{old => new}`, or `old => new`.
 */
private fun parseRenameBody(body: String): Pair<String, String>? {
    BRACE_RENAME_REGEX.matchEntire(body)?.let { match ->
        val prefix = match.groupValues[1]
        val oldLeaf = match.groupValues[2]
        val newLeaf = match.groupValues[3]
        return (prefix + oldLeaf) to (prefix + newLeaf)
    }
    val idx = body.indexOf(RENAME_ARROW)
    if (idx > 0) return body.substring(0, idx) to body.substring(idx + RENAME_ARROW.length)
    return null
}

