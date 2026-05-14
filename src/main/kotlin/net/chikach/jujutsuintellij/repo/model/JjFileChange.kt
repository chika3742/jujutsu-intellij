package net.chikach.jujutsuintellij.repo.model

data class JjFileChange(
    val status: Status,
    val path: String,
    val sourcePath: String? = null,
) {
    enum class Status { ADDED, MODIFIED, DELETED, RENAMED, COPIED }
}