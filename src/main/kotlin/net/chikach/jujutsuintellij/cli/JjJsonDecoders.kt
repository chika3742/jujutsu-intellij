package net.chikach.jujutsuintellij.cli

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

data class HistoryEntryJson(
    val commitId: String,
    val changeId: String,
    val authorName: String,
    val authorEmail: String,
    val date: Date,
    val description: String,
) {
    val author: String
        get() = JjJsonDecoders.formatAuthor(authorName, authorEmail)
}

data class LogEntryJson(
    val commitId: String,
    val changeId: String,
    val parentIds: List<String>,
    val authorName: String,
    val authorEmail: String,
    val authorTime: Date,
    val description: String,
)

data class TimedCommitJson(
    val commitId: String,
    val parentIds: List<String>,
    val time: Date,
)

data class AnnotationEntryJson(
    val commitId: String,
    val changeId: String,
    val authorName: String,
    val authorEmail: String,
    val date: Date,
    val lineNumber: Int,
) {
    val author: String
        get() = JjJsonDecoders.formatAuthor(authorName, authorEmail)
}

object JjJsonDecoders {
    fun decodeLogEntries(objects: List<JsonObject>): List<LogEntryJson> =
        objects.mapNotNull { obj ->
            val commitId = obj["ci"]?.jsonPrimitive?.content ?: return@mapNotNull null
            LogEntryJson(
                commitId = commitId,
                changeId = obj["ch"]?.jsonPrimitive?.content ?: "",
                parentIds = decodeStringArray(obj["p"]),
                authorName = obj["an"]?.jsonPrimitive?.content ?: "",
                authorEmail = obj["ae"]?.jsonPrimitive?.content ?: "",
                authorTime = parseTimestamp(obj["at"]?.jsonPrimitive?.content ?: "") ?: Date(0),
                description = obj["d"]?.jsonPrimitive?.content ?: "",
            )
        }

    fun decodeTimedCommits(objects: List<JsonObject>): List<TimedCommitJson> =
        objects.mapNotNull { obj ->
            val commitId = obj["ci"]?.jsonPrimitive?.content ?: return@mapNotNull null
            TimedCommitJson(
                commitId = commitId,
                parentIds = decodeStringArray(obj["p"]),
                time = parseTimestamp(obj["at"]?.jsonPrimitive?.content ?: "") ?: Date(0),
            )
        }

    private fun decodeStringArray(element: kotlinx.serialization.json.JsonElement?): List<String> {
        if (element == null) return emptyList()
        return element.jsonArray.map { it.jsonPrimitive.content }
    }

    fun decodeHistoryEntries(objects: List<JsonObject>): List<HistoryEntryJson> =
        objects.map { obj ->
            HistoryEntryJson(
                commitId = string(obj, "commitId"),
                changeId = string(obj, "changeId"),
                authorName = string(obj, "authorName"),
                authorEmail = string(obj, "authorEmail"),
                date = parseTimestamp(string(obj, "timestamp")) ?: Date(0),
                description = string(obj, "description").trimEnd('\n'),
            )
        }

    fun decodeAnnotationEntries(objects: List<JsonObject>): List<AnnotationEntryJson> =
        objects.map { obj ->
            AnnotationEntryJson(
                commitId = string(obj, "commitId"),
                changeId = string(obj, "changeId"),
                authorName = string(obj, "authorName"),
                authorEmail = string(obj, "authorEmail"),
                date = parseTimestamp(string(obj, "timestamp")) ?: Date(0),
                lineNumber = int(obj, "lineNumber"),
            )
        }

    fun formatAuthor(authorName: String, authorEmail: String): String =
        when {
            authorName.isNotBlank() && authorEmail.isNotBlank() -> "$authorName <$authorEmail>"
            authorName.isNotBlank() -> authorName
            else -> authorEmail
        }

    fun parseTimestamp(raw: String): Date? {
        if (raw.isBlank()) return null
        for (format in TIMESTAMP_FORMATS) {
            try {
                return SimpleDateFormat(format, Locale.ROOT).parse(raw)
            } catch (_: Exception) {
                // Try the next supported jj timestamp format.
            }
        }
        return null
    }

    private fun string(obj: JsonObject, field: String): String =
        obj[field]?.jsonPrimitive?.content
            ?: throw JjJsonException("Missing `$field` in JSON object: $obj")

    private fun int(obj: JsonObject, field: String): Int =
        obj[field]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw JjJsonException("Missing integer `$field` in JSON object: $obj")

    private val TIMESTAMP_FORMATS = listOf(
        "yyyy-MM-dd HH:mm:ss.SSS XXX",
        "yyyy-MM-dd HH:mm:ss XXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
}
