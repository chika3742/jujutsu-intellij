package net.chikach.jujutsuintellij.repo.model

data class JjBookmark(
    val name: String,
    val localCommitId: String?,
    val remoteRefs: List<JjBookmarkRemoteRef>,
)

data class JjBookmarkRemoteRef(
    val remote: String,
    val commitId: String,
)