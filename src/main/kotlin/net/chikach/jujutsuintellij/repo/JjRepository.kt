package net.chikach.jujutsuintellij.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.chikach.jujutsuintellij.cli.JjCommandResult
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.cli.JjJsonDecoders
import net.chikach.jujutsuintellij.cli.template.*
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

    fun abandon(revset: String = "@") {
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

    fun fileHistory(relativePath: String, revset: String = "::@"): List<JjHistoryEntry> {
        val rel = normalizeRelativePath(relativePath)
        val objects = commands().fileHistory(this, rel, HISTORY_TEMPLATE, revset)
        return JjJsonDecoders.decodeHistoryEntries(objects).map { it.toModel() }
    }

    fun annotateFile(relativePath: String, revision: String? = null): List<JjAnnotationLine> {
        val rel = normalizeRelativePath(relativePath)
        val objects = commands().annotateFile(this, rel, ANNOTATION_TEMPLATE, revision)
        return JjJsonDecoders.decodeAnnotationEntries(objects).map { it.toModel() }
    }

    /** Returns the text content of [relativePath] at [revision]. */
    fun showFile(revision: String, relativePath: String): String {
        val rel = normalizeRelativePath(relativePath)
        val result = commands().showFile(this, revision, rel)
        if (!result.isSuccess) throw JjOperationException.from("file show", result)
        return result.stdout
    }

    // ─── Log ────────────────────────────────────────────────────────────────

    fun recentLog(count: Int): List<JjLogEntry> {
        val objects = commands().recentLog(this, count, LOG_ENTRY_TEMPLATE)
        return JjJsonDecoders.decodeLogEntries(objects).map { it.toModel() }
    }

    fun allTimedCommits(): List<JjTimedCommit> {
        val objects = commands().allLog(this, TIMED_COMMIT_TEMPLATE)
        return JjJsonDecoders.decodeTimedCommits(objects).map { it.toModel() }
    }

    fun logByIds(commitIds: List<String>): List<JjLogEntry> {
        if (commitIds.isEmpty()) return emptyList()
        val objects = commands().logByIds(this, commitIds, LOG_ENTRY_TEMPLATE)
        return JjJsonDecoders.decodeLogEntries(objects).map { it.toModel() }
    }

    // ─── Bookmarks ──────────────────────────────────────────────────────────

    /**
     * Returns all bookmarks, grouped by name. Each [JjBookmark] carries its (optional) local
     * commit target and zero or more remote refs.
     */
    fun listBookmarks(): List<JjBookmark> {
        val objects = commands().bookmarkListJson(this, BOOKMARK_REF_TEMPLATE)
        return decodeBookmarks(objects)
    }

    /** Flattened (commit, bookmark name) pairs from `local_bookmarks()`. */
    fun bookmarksForLog(): List<JjBookmarkLogRef> {
        val result = commands().bookmarkCommitsForLog(this)
        if (!result.isSuccess) return emptyList()
        return parseBookmarksForLog(result.stdout)
    }

    fun createBookmark(name: String, revision: String = "@") {
        commands().bookmarkCreate(this, name, revision).orThrow("bookmark create")
    }

    fun deleteBookmark(name: String) {
        commands().bookmarkDelete(this, name).orThrow("bookmark delete")
    }

    fun setBookmark(name: String, revision: String = "@") {
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

        private val LOG_ENTRY_TEMPLATE: String by lazy {
            JjTemplates.commitJsonLine {
                obj {
                    "ci" to string(commitId)
                    "ch" to string(changeId)
                    "p" to serialized(parents.commitIds())
                    "an" to string(author.name())
                    "ae" to string(author.email())
                    "at" to string(author.timestamp())
                    "d" to string(description)
                }
            }
        }

        private val TIMED_COMMIT_TEMPLATE: String by lazy {
            JjTemplates.commitJsonLine {
                obj {
                    "ci" to string(commitId)
                    "p" to serialized(parents.commitIds())
                    "at" to string(author.timestamp())
                }
            }
        }

        private val HISTORY_TEMPLATE: String by lazy {
            JjTemplates.commitJsonLine {
                obj {
                    "commitId" to string(commitId)
                    "changeId" to string(changeId)
                    "authorName" to string(author.name())
                    "authorEmail" to string(author.email())
                    "timestamp" to string(author.timestamp())
                    "description" to string(description)
                }
            }
        }

        private val ANNOTATION_TEMPLATE: String by lazy {
            JjTemplates.annotationJsonLine {
                obj {
                    "commitId" to string(commitId)
                    "changeId" to string(changeId)
                    "authorName" to string(author.name())
                    "authorEmail" to string(author.email())
                    "timestamp" to string(author.timestamp())
                    "lineNumber" to num(lineNumber)
                }
            }
        }

        private val BOOKMARK_REF_TEMPLATE: String by lazy {
            JjTemplates.bookmarkRefJsonLine {
                obj {
                    "name" to string(name)
                    "remote" to string(remote)
                    "commitId" to string(normalTargetCommitId)
                }
            }
        }
    }
}

// ─── Internal converters: JSON decoders → domain objects ────────────────────

private fun net.chikach.jujutsuintellij.cli.LogEntryJson.toModel(): JjLogEntry =
    JjLogEntry(
        commitId = commitId,
        changeId = changeId,
        parentIds = parentIds,
        authorName = authorName,
        authorEmail = authorEmail,
        authorTime = authorTime,
        description = description,
    )

private fun net.chikach.jujutsuintellij.cli.TimedCommitJson.toModel(): JjTimedCommit =
    JjTimedCommit(commitId = commitId, parentIds = parentIds, time = time)

private fun net.chikach.jujutsuintellij.cli.HistoryEntryJson.toModel(): JjHistoryEntry =
    JjHistoryEntry(
        commitId = commitId,
        changeId = changeId,
        authorName = authorName,
        authorEmail = authorEmail,
        date = date,
        description = description,
    )

private fun net.chikach.jujutsuintellij.cli.AnnotationEntryJson.toModel(): JjAnnotationLine =
    JjAnnotationLine(
        commitId = commitId,
        changeId = changeId,
        authorName = authorName,
        authorEmail = authorEmail,
        date = date,
    )

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

private fun parseBookmarksForLog(stdout: String): List<JjBookmarkLogRef> =
    stdout.lineSequence()
        .filter { it.isNotBlank() }
        .flatMap { line ->
            val parts = line.split("\t")
            if (parts.size < 2) return@flatMap emptySequence()
            val commitId = parts[0].trim().takeIf { it.isNotBlank() } ?: return@flatMap emptySequence()
            parts.drop(1).asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { name -> JjBookmarkLogRef(commitId, name) }
        }
        .toList()

private fun decodeBookmarks(objects: List<JsonObject>): List<JjBookmark> {
    data class Acc(var local: String? = null, val remotes: MutableList<JjBookmarkRemoteRef> = mutableListOf())
    val grouped = LinkedHashMap<String, Acc>()
    for (obj in objects) {
        val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: continue
        val remote = obj["remote"]?.jsonPrimitive?.content.orEmpty()
        val commitId = obj["commitId"]?.jsonPrimitive?.content.orEmpty()
        val acc = grouped.getOrPut(name) { Acc() }
        when {
            remote.isEmpty() -> if (commitId.isNotEmpty()) acc.local = commitId
            commitId.isNotEmpty() -> acc.remotes += JjBookmarkRemoteRef(remote, commitId)
        }
    }
    return grouped.map { (name, acc) -> JjBookmark(name, acc.local, acc.remotes.toList()) }
}
