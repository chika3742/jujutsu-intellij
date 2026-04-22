package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer

/**
 * Marks the `.jj` administrative directory of every Jujutsu-mapped VCS root as excluded
 * content, matching the behaviour of `.git` in Git-backed projects. Excluded folders are
 * hidden from the Project View by default and skipped by indexing, search, and refactorings.
 */
class JjDirectoryIndexExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val jjVcs = JujutsuVcs.getInstance(project) ?: return emptyArray()
        val urls = mutableListOf<String>()
        for (root in vcsManager.getRootsUnderVcs(jjVcs)) {
            val jjDir = root.findChild(".jj") ?: continue
            urls.add(jjDir.url)
        }
        return urls.toTypedArray()
    }

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> =
        VirtualFilePointer.EMPTY_ARRAY
}
