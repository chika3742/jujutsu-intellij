package net.chikach.jujutsuintellij.repo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.chikach.jujutsuintellij.cli.JjDateSerializer
import net.chikach.jujutsuintellij.cli.template.*
import java.util.*

@Serializable
data class JjHistoryEntry(
    val commitId: String,
    val changeId: String,
    val authorName: String,
    val authorEmail: String,
    @SerialName("timestamp")
    @Serializable(with = JjDateSerializer::class)
    val date: Date,
    val description: String,
) {
    val author: String
        get() = formatAuthor(authorName, authorEmail)

    companion object {
        val TEMPLATE: String by lazy {
            JjTemplates.commitJsonLine {
                obj {
                    "commitId" to string(commitId())
                    "changeId" to string(changeId())
                    "authorName" to string(author().name())
                    "authorEmail" to string(author().email())
                    "timestamp" to string(author().timestamp().iso8601())
                    "description" to string(description())
                }
            }
        }
    }
}
