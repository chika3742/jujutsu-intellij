package net.chikach.jujutsuintellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class JjJsonCommand {

    inline fun <reified T> executeJsonList(request: JjCli.Request): List<T> {
        val result = JjCli.getInstance().execute(request)
        if (!result.isSuccess) {
            throw JjJsonException(
                "${result.commandLine} exited ${result.exitCode}: ${result.stderr.trim()}"
            )
        }
        return JjJsonParser.parseList<T>(result.stdout, result.commandLine)
    }

    companion object {
        @JvmStatic
        fun getInstance(): JjJsonCommand = service()
    }
}

object JjJsonParser {
    val json = Json {
        ignoreUnknownKeys = true
    }

    inline fun <reified T> parseList(stdout: String, commandLine: String): List<T> {
        if (stdout.isBlank()) return emptyList()

        val out = ArrayList<T>()
        stdout.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) return@forEachIndexed

            val value = try {
                json.decodeFromString<T>(line)
            } catch (e: Exception) {
                throw JjJsonException(
                    "Malformed JSON from `$commandLine` at line ${index + 1}: $line",
                    e,
                )
            }
            out += value
        }
        return out
    }
}

class JjJsonException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)