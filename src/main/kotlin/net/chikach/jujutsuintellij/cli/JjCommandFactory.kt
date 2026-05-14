package net.chikach.jujutsuintellij.cli

import java.nio.file.Path

internal object JjCommandFactory {
    const val VERSION_TIMEOUT_MS = 5_000L

    fun version(workDir: Path): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("--version"),
            timeoutMs = VERSION_TIMEOUT_MS,
        )

    fun diffSummary(
        workDir: Path,
        fromRef: String,
        toRef: String,
    ): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("diff", "--summary", "--from", fromRef, "--to", toRef),
        )

    fun fileHistory(
        workDir: Path,
        revset: String,
        template: String,
        relativePath: String,
    ): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("log", "--no-graph", "-r", revset, "-T", template, "--", relativePath),
        )

    fun annotateFile(
        workDir: Path,
        template: String,
        relativePath: String,
        revision: String? = null,
    ): JjCli.Request =
        request(
            workDir = workDir,
            args = buildList {
                add("file")
                add("annotate")
                add("-T")
                add(template)
                if (revision != null) {
                    add("-r")
                    add(revision)
                }
                add(relativePath)
            },
        )

    fun showFile(
        workDir: Path,
        revision: String,
        relativePath: String,
    ): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("file", "show", "-r", revision, relativePath),
        )

    fun describe(workDir: Path, message: String): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("describe", "-m", message),
        )

    fun newChange(workDir: Path): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("new"),
        )

    fun restore(workDir: Path, fromRevision: String, relativePaths: List<String>): JjCli.Request =
        request(
            workDir = workDir,
            args = buildList {
                add("restore")
                add("--from")
                add(fromRevision)
                addAll(relativePaths)
            },
        )

    fun abandon(workDir: Path, revset: String): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("abandon", revset),
        )

    fun recentLog(workDir: Path, count: Int, template: String): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("log", "--no-graph", "-r", "latest(all(), $count)", "-T", template),
        )

    fun allLog(workDir: Path, template: String): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("log", "--no-graph", "-r", "all()", "-T", template),
        )

    fun logByIds(workDir: Path, commitIds: List<String>, template: String): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("log", "--no-graph", "-r", commitIds.joinToString("|"), "-T", template),
        )

    fun bookmarkList(workDir: Path): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("bookmark", "list", "--all"),
        )

    fun configGet(workDir: Path, key: String): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("config", "get", key),
            timeoutMs = VERSION_TIMEOUT_MS,
        )

    fun getDescription(workDir: Path): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("log", "--no-graph", "-r", "@", "-T", "description"),
        )

    fun bookmarkCreate(workDir: Path, name: String, revision: String = "@"): JjCli.Request =
        request(workDir, listOf("bookmark", "create", "--revision", revision, name))

    fun bookmarkDelete(workDir: Path, name: String): JjCli.Request =
        request(workDir, listOf("bookmark", "delete", name))

    fun bookmarkSet(workDir: Path, name: String, revision: String = "@"): JjCli.Request =
        request(workDir, listOf("bookmark", "set", "--revision", revision, name))

    fun gitFetch(workDir: Path, remote: String? = null): JjCli.Request =
        request(workDir, buildList {
            add("git"); add("fetch")
            if (remote != null) { add("--remote"); add(remote) }
        })

    fun gitPush(workDir: Path, bookmark: String? = null, remote: String? = null): JjCli.Request =
        request(workDir, buildList {
            add("git"); add("push")
            if (bookmark != null) { add("--bookmark"); add(bookmark) }
            if (remote != null) { add("--remote"); add(remote) }
        })

    /** Returns lines of `<commitId>\t<bookmarkName1>\t<bookmarkName2>…` for each commit with local bookmarks. */
    fun bookmarkCommitsForLog(workDir: Path): JjCli.Request =
        request(workDir, listOf(
            "log", "--no-graph",
            "-r", "local_bookmarks()",
            "-T", "commit_id() ++ \"\\t\" ++ separate(\"\\t\", local_bookmarks()) ++ \"\\n\"",
        ))

    private fun request(
        workDir: Path,
        args: List<String>,
        timeoutMs: Long? = null,
    ): JjCli.Request {
        val base = JjCli.Request(workDir = workDir, args = args)
        return if (timeoutMs != null) base.copy(timeoutMs = timeoutMs) else base
    }
}
