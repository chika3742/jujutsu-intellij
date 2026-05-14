package net.chikach.jujutsuintellij.repo

import net.chikach.jujutsuintellij.cli.JjCommandResult

class JjOperationException(
    message: String,
    val commandLine: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val timedOut: Boolean,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {
        fun from(operation: String, result: JjCommandResult): JjOperationException {
            val message = result.stderr.trim().ifEmpty {
                if (result.timedOut) "jj $operation timed out"
                else "jj $operation failed (exit ${result.exitCode})"
            }
            return JjOperationException(
                message = message,
                commandLine = result.commandLine,
                stdout = result.stdout,
                stderr = result.stderr,
                exitCode = result.exitCode,
                timedOut = result.timedOut,
            )
        }
    }
}