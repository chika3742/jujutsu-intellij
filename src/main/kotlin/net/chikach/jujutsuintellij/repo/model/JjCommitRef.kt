package net.chikach.jujutsuintellij.repo.model

import kotlinx.serialization.Serializable
import net.chikach.jujutsuintellij.cli.template.*

/**
 * Decoded row from a jj `CommitRef` template (bookmark / tag).
 *
 * Mirrors a single line of `jj bookmark list -T <json>` output.
 *
 * - `remote == null` for a local ref; otherwise the remote name.
 * - `commitId == null` when the ref is conflicted (no `normal_target()`).
 */
@Serializable
data class JjCommitRef(
    val name: String,
    val remote: String?,
    val commitId: String?,
) {
    val isLocal: Boolean get() = remote == null

    companion object {
        val TEMPLATE: String by lazy {
            JjTemplates.commitRefJsonLine {
                obj {
                    "name" to string(name())
                    "remote" to serialized(remote())
                    "commitId" to rawJson(normalTargetCommitIdJson())
                }
            }
        }
    }
}
