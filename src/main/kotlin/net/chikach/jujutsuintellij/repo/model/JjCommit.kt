package net.chikach.jujutsuintellij.repo.model

import kotlinx.serialization.Serializable
import net.chikach.jujutsuintellij.cli.JjDateSerializer
import net.chikach.jujutsuintellij.cli.template.*
import java.util.*

@Serializable
data class JjCommit(
    val commitId: String,
    val changeId: String,
    val parentIds: List<String>,
    val authorName: String,
    val authorEmail: String,
    @Serializable(with = JjDateSerializer::class) val authorTime: Date,
    val description: String,
    val bookmarks: List<String>,
    val isRoot: Boolean = false,
) {
    companion object {
        val TEMPLATE: String by lazy {
            JjTemplates.commitJsonLine {
                obj {
                    "commitId" to string(commitId())
                    "changeId" to string(changeId())
                    "parentIds" to serialized(parents().commitIds())
                    "authorName" to string(author().name())
                    "authorEmail" to string(author().email())
                    "authorTime" to string(author().timestamp().iso8601())
                    "description" to string(description())
                    "bookmarks" to serialized(bookmarks())
                    "isRoot" to bool(root())
                }
            }
        }
    }
}
