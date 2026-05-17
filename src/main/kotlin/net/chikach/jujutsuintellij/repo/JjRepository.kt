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

    fun describe(message: String) {
        commands().describe(this, message).orThrow("describe")
    }

    fun newChange() {
        commands().newChange(this).orThrow("new")
    }

    /** Sets the description on `@` and creates a new working-copy on top. */
    fun commit(message: String) {
        commands().commit(this, message).orThrow("commit")
    }

    fun abandon(revset: String = WORKING_COPY_REF) {
        commands().abandon(this, revset).orThrow("abandon")
    }

    fun restore(fromRevision: String, relativePaths: List<String>) {
        val normalized = relativePaths.map { normalizeRelativePath(it) }
        commands().restore(this, fromRevision, normalized).orThrow("restore")
    }

    // ─── Diff / Local Changes ───────────────────────────────────────────────

    /** Changes between `@-` (parent) and `@` (working-copy). */
    fun workingCopyChanges(): List<JjFileChange> = diffSummary(PARENT_REF, WORKING_COPY_REF)

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

    // ─── Bookmarks ──────────────────────────────────────────────────────────

    /**
     * Returns all bookmark / tag refs as one [JjCommitRef] per (name, remote?) pair.
     */
    fun listBookmarks(revset: String? = null): List<JjCommitRef> =
        commands().bookmarkList(this, revset)

    fun createBookmark(name: String, revision: String = WORKING_COPY_REF) {
        commands().bookmarkCreate(this, name, revision).orThrow("bookmark create")
    }

    fun deleteBookmark(name: String) {
        commands().bookmarkDelete(this, name).orThrow("bookmark delete")
    }

    fun setBookmark(name: String, revision: String = WORKING_COPY_REF) {
        commands().bookmarkSet(this, name, revision).orThrow("bookmark set")
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

    override fun toString(): String = "JjRepository(root=$rootPath)"

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun commands(): JjCommands = JjCommands.getInstance()

    private fun JjCommandResult.orThrow(operation: String) {
        if (!isSuccess) throw JjOperationException.from(operation, this)
    }

    companion object {
        private const val PARENT_REF = "@-"
        private const val WORKING_COPY_REF = "@"
        private const val ANCESTORS_OF_WORKING_COPY_REF = "::@"
    }
}

// ─── Internal parsers ───────────────────────────────────────────────────────

private fun parseDiffSummary(stdout: String): List<JjFileChange> {
    val arrow = " => "
    val braceRename = Regex("""^(.*?)\{(.*?) => (.*?)\}$""")
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
                val (old, new) = parseRenameBody(body, braceRename, arrow) ?: continue
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
private fun parseRenameBody(body: String, brace: Regex, arrow: String): Pair<String, String>? {
    brace.matchEntire(body)?.let { match ->
        val prefix = match.groupValues[1]
        val oldLeaf = match.groupValues[2]
        val newLeaf = match.groupValues[3]
        return (prefix + oldLeaf) to (prefix + newLeaf)
    }
    val idx = body.indexOf(arrow)
    if (idx > 0) return body.substring(0, idx) to body.substring(idx + arrow.length)
    return null
}

