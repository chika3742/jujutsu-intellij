package net.chikach.jujutsuintellij.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * In-memory handle for a single Jujutsu working copy rooted at [root].
 *
 * Holds per-repo caches that later phases will populate (working-copy change, bookmarks,
 * operation head, etc.). Phase 2a keeps this intentionally minimal.
 */
class JjRepository(
    val project: Project,
    val root: VirtualFile,
) {
    val rootPath: String get() = root.path

    override fun toString(): String = "JjRepository(root=$rootPath)"
}
