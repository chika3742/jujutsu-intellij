package net.chikach.jujutsuintellij.cli

data class JjCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val commandLine: String,
    val durationMs: Long,
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0
}

class JjCliException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
