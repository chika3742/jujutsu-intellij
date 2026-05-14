package net.chikach.jujutsuintellij.repo.model

import java.util.*

data class JjLogEntry(
    val commitId: String,
    val changeId: String,
    val parentIds: List<String>,
    val authorName: String,
    val authorEmail: String,
    val authorTime: Date,
    val description: String,
)
