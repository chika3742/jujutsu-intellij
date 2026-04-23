package net.chikach.jujutsuintellij.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import net.chikach.jujutsuintellij.config.JujutsuAppSettings
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

@Service(Service.Level.APP)
class JjCli {

    data class Request(
        val workDir: Path,
        val args: List<String>,
        val stdin: String? = null,
        val timeoutMs: Long = JujutsuAppSettings.currentCommandTimeoutMs(),
        val env: Map<String, String> = emptyMap(),
    )

    /**
     * Executes a `jj` subprocess synchronously. MUST NOT be called on the EDT.
     *
     * Throws [JjCliException] when the executable cannot be resolved or the process cannot be started.
     * A non-zero exit code is reflected in [JjCommandResult.exitCode] rather than thrown, since
     * `jj` uses exit codes to signal normal operational outcomes (e.g., no changes).
     */
    fun execute(request: Request): JjCommandResult {
        val executable = JujutsuAppSettings.getInstance().resolvedExecutablePath()
            ?: throw JjCliException("jj executable not found; configure it in Settings | Tools | Jujutsu")

        val cmdLine = GeneralCommandLine(executable).apply {
            withWorkDirectory(request.workDir.toFile())
            addParameters(request.args)
            charset = StandardCharsets.UTF_8
            request.env.forEach { (k, v) -> withEnvironment(k, v) }
            // Keep output deterministic and non-interactive.
            withEnvironment("NO_COLOR", "1")
            withEnvironment("JJ_PAGER", "")
        }

        val startedAt = System.currentTimeMillis()
        val handler = try {
            CapturingProcessHandler(cmdLine)
        } catch (e: ExecutionException) {
            throw JjCliException("Failed to start jj process: ${e.message}", e)
        }

        try {
            handler.processInput.use { stdinStream ->
                request.stdin?.let { input ->
                    stdinStream.write(input.toByteArray(StandardCharsets.UTF_8))
                    stdinStream.flush()
                }
            }
        } catch (e: IOException) {
            LOG.warn("Failed writing stdin to jj process", e)
        }

        val timeoutCapped = request.timeoutMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val output = handler.runProcess(timeoutCapped, true)
        val duration = System.currentTimeMillis() - startedAt

        val result = JjCommandResult(
            exitCode = if (output.isTimeout) -1 else output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
            commandLine = cmdLine.commandLineString,
            durationMs = duration,
            timedOut = output.isTimeout,
        )
        if (LOG.isDebugEnabled) {
            LOG.debug("jj exec: '${result.commandLine}' -> exit=${result.exitCode} in ${result.durationMs}ms")
        }
        return result
    }

    companion object {
        private val LOG = logger<JjCli>()

        @JvmStatic
        fun getInstance(): JjCli = service()
    }
}
