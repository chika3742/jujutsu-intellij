package net.chikach.jujutsuintellij.repo.model

import net.chikach.jujutsuintellij.cli.JjJsonDecoders
import java.util.*

data class JjHistoryEntry(
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
