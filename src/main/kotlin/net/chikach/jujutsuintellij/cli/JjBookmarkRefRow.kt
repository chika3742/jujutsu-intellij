package net.chikach.jujutsuintellij.cli

import kotlinx.serialization.Serializable
import net.chikach.jujutsuintellij.cli.template.*
import org.jetbrains.annotations.ApiStatus

/**
 * One row from the bookmark-ref template (`{name, remote, commitId}`).
 * Grouped into [net.chikach.jujutsuintellij.repo.model.JjBookmark] by [net.chikach.jujutsuintellij.repo.JjRepository].
 */
@ApiStatus.Internal
@Serializable
internal data class JjBookmarkRefRow(
    val name: String,
    val remote: String = "",
    val commitId: String = "",
) {
    companion object {
        val TEMPLATE: String by lazy {
            JjTemplates.bookmarkRefJsonLine {
                obj {
                    "name" to string(name)
                    "remote" to string(remote)
                    "commitId" to string(normalTargetCommitId)
                }
            }
        }
    }
}