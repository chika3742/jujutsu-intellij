package net.chikach.jujutsuintellij.repo.model

import kotlinx.serialization.Serializable
import net.chikach.jujutsuintellij.cli.JjDateSerializer
import net.chikach.jujutsuintellij.cli.template.*
import java.util.Date

@Serializable
data class JjTimedCommit(
    val commitId: String,
    val parentIds: List<String>,
    @Serializable(with = JjDateSerializer::class) val time: Date,
) {
    companion object {
        val TEMPLATE: String by lazy {
            JjTemplates.commitJsonLine {
                obj {
                    "commitId" to string(commitId)
                    "parentIds" to serialized(parents.commitIds())
                    "time" to string(author.timestamp().iso8601())
                }
            }
        }
    }
}