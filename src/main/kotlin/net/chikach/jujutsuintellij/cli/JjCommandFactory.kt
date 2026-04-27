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

    fun getDescription(workDir: Path): JjCli.Request =
        request(
            workDir = workDir,
            args = listOf("log", "--no-graph", "-r", "@", "-T", "description"),
        )

    private fun request(
        workDir: Path,
        args: List<String>,
        timeoutMs: Long? = null,
    ): JjCli.Request {
        val base = JjCli.Request(workDir = workDir, args = args)
        return if (timeoutMs != null) base.copy(timeoutMs = timeoutMs) else base
    }
}
