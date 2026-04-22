package net.chikach.jujutsuintellij.model

import com.intellij.openapi.vcs.history.VcsRevisionNumber

/**
 * Minimal [VcsRevisionNumber] backed by an arbitrary jj revision reference: a commit_id,
 * a change_id, or a symbolic expression like `@` / `@-`. Comparison is lexicographic and
 * therefore stable but not semantically meaningful — callers that need ordering should
 * resolve to operation ids through `jj op log` and compare those instead.
 */
class JjRevisionNumber(private val revision: String) : VcsRevisionNumber {

    override fun asString(): String = revision

    override fun compareTo(other: VcsRevisionNumber?): Int {
        if (other !is JjRevisionNumber) return 0
        return revision.compareTo(other.revision)
    }

    override fun toString(): String = revision

    override fun equals(other: Any?): Boolean {
        return other is JjRevisionNumber && revision == other.revision
    }

    override fun hashCode(): Int = revision.hashCode()

    companion object {
        val WORKING_COPY: JjRevisionNumber = JjRevisionNumber("@")
        val WORKING_COPY_PARENT: JjRevisionNumber = JjRevisionNumber("@-")
    }
}
