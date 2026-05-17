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

    fun describe(repo: JjRepository, message: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("describe", "-m", message)))

    fun newChange(repo: JjRepository): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("new")))

    fun commit(repo: JjRepository, message: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("commit", "-m", message)))

    fun restore(repo: JjRepository, fromRevision: String, relativePaths: List<String>): JjCommandResult {
        val args = buildList {
            add("restore")
            add("--from"); add(fromRevision)
            addAll(relativePaths)
        }
        return execute(request(repo.rootPathNio, args))
    }

    fun abandon(repo: JjRepository, revset: String): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("abandon", revset)))

    fun getDescription(repo: JjRepository): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("log", "--no-graph", "-r", "@", "-T", "description")))

    fun log(repo: JjRepository, revset: String): List<JjCommit> =
        JjJsonCommand.getInstance().executeJsonList(
            request(repo.rootPathNio, listOf("log", "--no-graph", "-r", revset, "-T", JjCommit.TEMPLATE))
        )

    fun bookmarkList(repo: JjRepository, revset: String? = null): List<JjCommitRef> {
        val args = buildList {
            add("bookmark")
            add("list")
            if (revset != null) { add("-r"); add(revset) }
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

    fun bookmarkSet(repo: JjRepository, name: String, revision: String = "@"): JjCommandResult =
        execute(request(repo.rootPathNio, listOf("bookmark", "set", "--revision", revision, name)))

    fun gitFetch(repo: JjRepository, remote: String? = null): JjCommandResult {
        val args = buildList {
            add("git")
            add("fetch")
            if (remote != null) { add("--remote"); add(remote) }
        }
        return execute(request(repo.rootPathNio, args))
    }

    fun gitPush(repo: JjRepository, bookmark: String? = null, remote: String? = null): JjCommandResult {
        val args = buildList {
            add("git")
            add("push")
            if (bookmark != null) { add("--bookmark"); add(bookmark) }
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
