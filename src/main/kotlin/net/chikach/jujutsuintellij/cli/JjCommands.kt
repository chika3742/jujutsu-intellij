package net.chikach.jujutsuintellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.model.JjAnnotationLine
import net.chikach.jujutsuintellij.repo.model.JjCommit
import net.chikach.jujutsuintellij.repo.model.JjCommitRef
import net.chikach.jujutsuintellij.repo.model.JjHistoryEntry
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Low-level wrapper around the `jj` CLI. Call this from [net.chikach.jujutsuintellij.repo.JjRepository]
 * (or [net.chikach.jujutsuintellij.cli.JjVersion] for the pre-repo `--version` check). All other
 * callers should go through the repository layer.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class JjCommands {

    fun version(workDir: Path): JjCommandResult =
        execute(request(workDir, listOf("--version"), timeoutMs = VERSION_TIMEOUT_MS))

    fun diffSummary(
        repo: JjRepository,
        fromRef: String,
        toRef: String,
    ): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("diff", "--summary", "--from", fromRef, "--to", toRef)))

    fun fileHistory(
        repo: JjRepository,
        relativePath: String,
        revset: String = DEFAULT_LOG_REVSET,
    ): List<JjHistoryEntry> {
        val rel = repo.normalizeRelativePath(relativePath)
        return JjJsonCommand.getInstance().executeJsonList(
            request(repo.rootPathNio, listOf("log", "--no-graph", "-r", revset, "-T", JjHistoryEntry.TEMPLATE, "--", rel))
        )
    }

    fun annotateFile(
        repo: JjRepository,
        relativePath: String,
        revision: String? = null,
    ): List<JjAnnotationLine> {
        val rel = repo.normalizeRelativePath(relativePath)
        val args = buildList {
            add("file")
            add("annotate")
            add("-T"); add(JjAnnotationLine.TEMPLATE)
            if (revision != null) { add("-r"); add(revision) }
            add(rel)
        }
        return JjJsonCommand.getInstance().executeJsonList(request(repo.rootPathNio, args))
    }

    fun showFile(
        repo: JjRepository,
        revision: String,
        relativePath: String,
    ): JjCommandResult {
        val rel = repo.normalizeRelativePath(relativePath)
        return execute(request(repo.rootPathNio, listOf("file", "show", "-r", revision, rel)))
    }

    fun describe(repo: JjRepository, message: String, revision: String? = null): JjCommandResult {
        val args = buildList {
            add("describe")
            if (revision != null) add(revision)
            add("-m"); add(message)
        }
        return execute(request(repo.rootPathNio, args))
    }

    fun squash(repo: JjRepository, from: String, into: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("squash", "--from", from, "--into", into)))

    /** [modeFlag] is `-r` (the revisions only) or `-s` (the revisions and their descendants). */
    fun rebase(repo: JjRepository, modeFlag: String, revisions: String, onto: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("rebase", modeFlag, revisions, "-o", onto)))

    fun new(repo: JjRepository, revision: String? = null): JjCommandResult {
        val args = buildList {
            add("new")
            if (revision != null) add(revision)
        }
        return execute(request(repo.rootPathNio, args))
    }

    fun commit(repo: JjRepository, message: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("commit", "-m", message)))

    /**
     * `jj restore <paths>` with no `--from`: restores the paths into `@` from its parent(s).
     * jj auto-merges multiple parents, so this is correct for merge commits too — unlike an
     * explicit `--from @-`, which errors with "resolved to more than one revision" on a merge.
     */
    fun restore(repo: JjRepository, relativePaths: List<String>): JjCommandResult {
        val args = buildList {
            add("restore")
            addAll(relativePaths)
        }
        return execute(request(repo.rootPathNio, args))
    }

    fun abandon(repo: JjRepository, revset: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("abandon", revset)))

    fun edit(repo: JjRepository, revision: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("edit", revision)))

    fun getDescription(repo: JjRepository): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("log", "--no-graph", "-r", "@", "-T", "description")))

    fun log(repo: JjRepository, revset: String): List<JjCommit> =
        JjJsonCommand.getInstance().executeJsonList(
            request(repo.rootPathNio, listOf("log", "--no-graph", "-r", revset, "-T", JjCommit.TEMPLATE))
        )

    fun bookmarkList(
        repo: JjRepository,
        revset: String? = null,
        sortKeys: String? = null,
        allRemotes: Boolean = false,
    ): List<JjCommitRef> {
        val args = buildList {
            add("bookmark")
            add("list")
            if (revset != null) { add("-r"); add(revset) }
            if (sortKeys != null) { add("--sort"); add(sortKeys) }
            if (allRemotes) add("--all-remotes")
            add("-T"); add(JjCommitRef.TEMPLATE)
        }
        return JjJsonCommand.getInstance().executeJsonList(request(repo.rootPathNio, args))
    }

    fun configGet(repo: JjRepository, key: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("config", "get", key), timeoutMs = VERSION_TIMEOUT_MS))

    fun bookmarkCreate(repo: JjRepository, name: String, revision: String = "@"): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("bookmark", "create", "--revision", revision, name)))

    fun bookmarkDelete(repo: JjRepository, name: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("bookmark", "delete", name)))

    /**
     * Creates or updates [name] to point at [revision] (`jj bookmark set`). [allowBackwards] adds
     * `--allow-backwards`, which jj requires to move a bookmark backwards or sideways.
     */
    fun bookmarkSet(repo: JjRepository, name: String, revision: String, allowBackwards: Boolean = false): JjCommandResult {
        val args = buildList {
            add("bookmark"); add("set")
            add("--revision"); add(revision)
            if (allowBackwards) add("--allow-backwards")
            add(name)
        }
        return execute(request(repo.rootPathNio, args))
    }

    fun bookmarkRename(repo: JjRepository, oldName: String, newName: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("bookmark", "rename", oldName, newName)))

    /** Drops [name] locally without recording a deletion to push (`jj bookmark forget`). */
    fun bookmarkForget(repo: JjRepository, name: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("bookmark", "forget", name)))

    fun bookmarkTrack(repo: JjRepository, name: String, remote: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("bookmark", "track", "$name@$remote")))

    fun bookmarkUntrack(repo: JjRepository, name: String, remote: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("bookmark", "untrack", "$name@$remote")))

    fun gitFetch(repo: JjRepository, remote: String? = null): JjCommandResult {
        val args = buildList {
            add("git")
            add("fetch")
            if (remote != null) { add("--remote"); add(remote) }
        }
        return execute(request(repo.rootPathNio, args))
    }

    fun gitPush(repo: JjRepository, bookmarks: List<String> = emptyList(), remote: String? = null): JjCommandResult {
        val args = buildList {
            add("git")
            add("push")
            for (bookmark in bookmarks) { add("--bookmark"); add(bookmark) }
            if (remote != null) { add("--remote"); add(remote) }
        }
        return execute(request(repo.rootPathNio, args))
    }

    private fun execute(request: JjCli.Request): JjCommandResult =
        JjCli.getInstance().execute(request)

    private fun request(
        workDir: Path,
        args: List<String>,
        timeoutMs: Long? = null,
    ): JjCli.Request {
        val base = JjCli.Request(workDir = workDir, args = args)
        return if (timeoutMs != null) base.copy(timeoutMs = timeoutMs) else base
    }

    companion object {
        const val VERSION_TIMEOUT_MS = 5_000L
        private const val DEFAULT_LOG_REVSET = "::@"

        @JvmStatic
        fun getInstance(): JjCommands = service()
    }
}
