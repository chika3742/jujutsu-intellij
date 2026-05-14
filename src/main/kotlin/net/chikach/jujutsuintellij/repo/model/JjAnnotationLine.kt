package net.chikach.jujutsuintellij.repo.model

import java.util.*

/**
 * One annotation record — the jj commit that last touched a given source line.
 * The line number is implicit in the position within the returned list.
 */
data class JjAnnotationLine(
    val commitId: String,
    val changeId: String,
    val authorName: String,
    val authorEmail: String,
    val date: Date,
)
