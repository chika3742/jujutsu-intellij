package net.chikach.jujutsuintellij.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Path

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
    val rootPathNio: Path get() = Path.of(rootPath)

    fun normalizeRelativePath(relativePath: String): String =
        JjPathUtil.normalizeRelativePath(relativePath)

    fun relativize(absolutePath: String): String? =
        JjPathUtil.relativize(rootPath, absolutePath)

    fun containsPath(absolutePath: String): Boolean =
        JjPathUtil.isUnderRoot(rootPath, absolutePath)

    fun resolveRelativePath(relativePath: String): File =
        File(rootPath, normalizeRelativePath(relativePath))

    override fun toString(): String = "JjRepository(root=$rootPath)"
}
