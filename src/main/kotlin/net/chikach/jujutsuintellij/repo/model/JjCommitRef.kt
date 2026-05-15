package net.chikach.jujutsuintellij.repo.model

import kotlinx.serialization.Serializable

@Serializable
data class JjCommitRef(
    val name: String,
    val remoteName: String?,
    val hasConflict: Boolean,
    /**
     * Target commit if the ref is not conflicted and points to a commit.
     */
    val normalTarget: JjCommit?,
) {
    companion object {
    }
}
