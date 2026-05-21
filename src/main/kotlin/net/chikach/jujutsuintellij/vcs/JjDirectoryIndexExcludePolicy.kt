package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.pointers.VirtualFilePointer

/**
 * Marks every `.jj` administrative directory under the project's content roots as excluded
 * content, matching the behaviour of `.git` in Git-backed projects. Excluded folders are hidden
 * from the Project View, skipped by indexing/search/refactorings, and — crucially — not tracked
 * by Local History (`IdeaGateway.isVersioned` skips excluded content).
 *
 * Detection keys off the `.jj` directory itself rather than the VCS mapping: the directory index
 * may query this policy before the Jujutsu VCS mapping is established, and a stale empty result
 * would leave the churning `.jj/repo` store tracked by Local History.
 */
class JjDirectoryIndexExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> =
        ProjectRootManager.getInstance(project).contentRoots
            .mapNotNull { it.findChild(".jj")?.url }
            .toTypedArray()

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> =
        VirtualFilePointer.EMPTY_ARRAY
}
