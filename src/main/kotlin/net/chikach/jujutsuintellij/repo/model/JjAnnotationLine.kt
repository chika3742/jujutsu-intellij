package net.chikach.jujutsuintellij.repo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.chikach.jujutsuintellij.cli.JjDateSerializer
import net.chikach.jujutsuintellij.cli.template.*
import java.util.Date

/**
 * One annotation record — the jj commit that last touched a given source line.
 * The line number is implicit in the position within the returned list.
 */
@Serializable
data class JjAnnotationLine(
    val commitId: String,
    val changeId: String,
    val authorName: String,
    val authorEmail: String,
    @SerialName("timestamp")
    @Serializable(with = JjDateSerializer::class)
    val date: Date,
) {
    val author: String
        get() = formatAuthor(authorName, authorEmail)

    companion object {
        val TEMPLATE: String by lazy {
            JjTemplates.annotationJsonLine {
                obj {
                    "commitId" to string(commit().commitId())
                    "changeId" to string(commit().changeId())
                    "authorName" to string(commit().author().name())
                    "authorEmail" to string(commit().author().email())
                    "timestamp" to string(commit().author().timestamp().iso8601())
                }
            }
        }
    }
}