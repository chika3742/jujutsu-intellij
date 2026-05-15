package net.chikach.jujutsuintellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.json.JsonObject
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class JjJsonCommand {

    fun executeObjects(request: JjCli.Request): List<JsonObject> {
        val result = JjCli.getInstance().execute(request)
        if (!result.isSuccess) {
            throw JjJsonException(
                "${result.commandLine} exited ${result.exitCode}: ${result.stderr.trim()}"
            )
        }
        return JjJsonParser.parseObjects(result.stdout, result.commandLine)
    }

    companion object {
        @JvmStatic
        fun getInstance(): JjJsonCommand = service()
    }
}

object JjJsonParser {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }

    fun parseObjects(stdout: String, commandLine: String): List<JsonObject> {
        if (stdout.isBlank()) return emptyList()

        val objects = ArrayList<JsonObject>()
        stdout.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) return@forEachIndexed

            val element = try {
                json.parseToJsonElement(line)
            } catch (e: Exception) {
                throw JjJsonException("Malformed JSON from `$commandLine` at line ${index + 1}: $line", e)
            }
            val obj = element as? JsonObject
                ?: throw JjJsonException("Expected JSON object from `$commandLine` at line ${index + 1}")
            objects += obj
        }
        return objects
    }
}

class JjJsonException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
