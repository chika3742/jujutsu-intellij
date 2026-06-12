package net.chikach.jujutsuintellij.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import net.chikach.jujutsuintellij.repo.JjOperationException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Remote tag operations that jj itself does not support, executed with the `git` CLI against the
 * repository's backing git repo.
 *
 * Every jj repo has a backing git repository: `.git` at the workspace root when co-located,
 * otherwise a bare repo at `.jj/repo/store/git` (or wherever `.jj/repo/store/git_target` points).
 * Remotes are configured in that git repo, so `git --git-dir=<resolved>` works in both layouts.
 */
object GitRemoteTagOperations {

    /**
     * Deletes tag [name] on [remote] (`git --git-dir=<dir> push <remote> --delete refs/tags/<name>`).
     * Throws [JjOperationException] when git cannot be started, fails, or times out — the deletion
     * is a direct user action, so failures must surface instead of being logged away.
     */
    fun deleteRemoteTag(repoRoot: Path, remote: String, name: String, timeoutMs: Long = 30_000) {
        val operation = "git push $remote --delete refs/tags/$name"
        val cmdLine = GeneralCommandLine("git")
            .withWorkDirectory(repoRoot.toFile())
            .withParameters("--git-dir", resolveGitDir(repoRoot).toString(), "push", remote, "--delete", "refs/tags/$name")
            .apply { charset = StandardCharsets.UTF_8 }

        val handler = try {
            CapturingProcessHandler(cmdLine)
        } catch (e: ExecutionException) {
            throw JjOperationException(
                message = "Failed to start git: ${e.message}",
                commandLine = cmdLine.commandLineString,
                stdout = "",
                stderr = "",
                exitCode = -1,
                timedOut = false,
                cause = e,
            )
        }

        val timeout = timeoutMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val output = handler.runProcess(timeout, true)
        if (output.isTimeout || output.exitCode != 0) {
            val message = output.stderr.trim().ifEmpty {
                if (output.isTimeout) "$operation timed out"
                else "$operation failed (exit ${output.exitCode})"
            }
            throw JjOperationException(
                message = message,
                commandLine = cmdLine.commandLineString,
                stdout = output.stdout,
                stderr = output.stderr,
                exitCode = output.exitCode,
                timedOut = output.isTimeout,
            )
        }
    }

    /**
     * Resolves the backing git directory: `.git` for co-located repos, the `git_target` pointer when
     * present (relative paths resolve against the store directory), and the in-store bare repo
     * otherwise.
     */
    private fun resolveGitDir(repoRoot: Path): Path {
        val colocated = repoRoot.resolve(".git")
        if (Files.exists(colocated)) return colocated

        val store = repoRoot.resolve(".jj/repo/store")
        val gitTarget = store.resolve("git_target")
        if (Files.isRegularFile(gitTarget)) {
            val target = Files.readString(gitTarget).trim()
            if (target.isNotEmpty()) return store.resolve(target).normalize()
        }
        return store.resolve("git")
    }
}
