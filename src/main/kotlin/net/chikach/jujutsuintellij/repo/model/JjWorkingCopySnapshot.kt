package net.chikach.jujutsuintellij.repo.model

import kotlinx.serialization.Serializable
import net.chikach.jujutsuintellij.cli.template.*

/**
 * One-shot snapshot of the working-copy commit (`@`): identity, parents, conflicted files, and the
 * diff of `@` against its parent(s) — all returned by a single `jj log -r @` invocation.
 *
 * `self.diff()` in the jj template language compares the commit against its parent(s). For a
 * single-parent commit it is equivalent to `jj diff --from first_parent(@) --to @`, but for a
 * multi-parent (merge) commit it returns the diff against the parents' common ancestor instead.
 * Use [firstParentChanges] to obtain a [JjFileChange] list only when this snapshot was taken on a
 * single-parent commit; for merge commits the caller must fall back to `JjRepository.workingCopyChanges`.
 */
@Serializable
data class JjWorkingCopySnapshot(
    val commitId: String,
    val parentIds: List<String>,
    val conflictedFiles: List<String>,
    val diffSummary: List<DiffEntry>,
) {

    /** Mirrors a single line of `jj diff --summary`. */
    @Serializable
    data class DiffEntry(
        /** Single-character status: `"M"`, `"A"`, `"D"`, `"R"`, or `"C"`. */
        val status: String,
        val path: String,
        /**
         * Source path for renames/copies. For A/M/D entries jj's `TreeDiffEntry.source()` resolves
         * to the same path as [path], so the JSON field is always non-null and we never read it for
         * non-rename/non-copy statuses.
         */
        val sourcePath: String,
    )

    /**
     * The diff of `@` against its single parent as [JjFileChange]s, or `null` if `@` has zero or
     * multiple parents (in which case the template diff does not match `jj diff --from first_parent(@)`).
     * Entries with an unrecognized status are dropped, mirroring `parseDiffSummary`.
     */
    fun firstParentChanges(): List<JjFileChange>? {
        if (parentIds.size != 1) return null
        return diffSummary.mapNotNull { entry ->
            when (entry.status) {
                "A" -> JjFileChange(JjFileChange.Status.ADDED, entry.path)
                "M" -> JjFileChange(JjFileChange.Status.MODIFIED, entry.path)
                "D" -> JjFileChange(JjFileChange.Status.DELETED, entry.path)
                "R" -> JjFileChange(JjFileChange.Status.RENAMED, entry.path, sourcePath = entry.sourcePath)
                "C" -> JjFileChange(JjFileChange.Status.COPIED, entry.path, sourcePath = entry.sourcePath)
                else -> null
            }
        }
    }

    companion object {
        val TEMPLATE: String by lazy {
            JjTemplates.commitJsonLine {
                obj {
                    "commitId" to string(commitId())
                    "parentIds" to serialized(parents().commitIds())
                    "conflictedFiles" to serialized(conflictedFilePaths())
                    "diffSummary" to diffFiles().jsonObjectArray(::treeDiffEntryExpr) { entry ->
                        "status" to string(entry.statusChar())
                        "path" to serialized(entry.path())
                        "sourcePath" to serialized(entry.source().path())
                    }
                }
            }
        }
    }
}
