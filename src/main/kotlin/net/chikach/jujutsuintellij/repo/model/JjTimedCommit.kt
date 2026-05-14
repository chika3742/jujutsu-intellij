package net.chikach.jujutsuintellij.repo.model

import java.util.*

data class JjTimedCommit(
    val commitId: String,
    val parentIds: List<String>,
    val time: Date,
)
