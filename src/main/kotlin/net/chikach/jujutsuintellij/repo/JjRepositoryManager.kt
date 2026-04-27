package net.chikach.jujutsuintellij.repo

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.jujutsuintellij.vcs.JujutsuVcs
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-scoped registry of [JjRepository] instances, keyed by VCS root [VirtualFile].
 *
 * Repositories are lazily created the first time a caller asks for one, and kept alive for the
 * project lifetime. Invalidation happens when a root disappears (caller responsibility for now;
 * later phases wire this to VCS mapping change events).
 */
@Service(Service.Level.PROJECT)
class JjRepositoryManager(private val project: Project) {

    private val repositories = ConcurrentHashMap<VirtualFile, JjRepository>()

    /** Returns the repository for an explicit VCS root, creating one if missing. */
    fun getRepositoryForRoot(root: VirtualFile): JjRepository =
        repositories.computeIfAbsent(root) { JjRepository(project, it) }

    /** Returns the Jujutsu repository containing [file], or null if the file is outside any jj root. */
    fun getRepositoryForFile(file: VirtualFile): JjRepository? {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val vcs = vcsManager.getVcsFor(file) ?: return null
        if (vcs.name != JujutsuVcs.VCS_NAME) return null
        val root = vcsManager.getVcsRootFor(file) ?: return null
        return getRepositoryForRoot(root)
    }

    /**
     * Returns all Jujutsu repositories in the project.
     *
     * The first call scans [ProjectLevelVcsManager.allVersionedRoots] to discover roots that
     * haven't been accessed yet (e.g. on startup before any file is opened), then caches them.
     */
    fun getAll(): Collection<JjRepository> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        for (root in vcsManager.allVersionedRoots) {
            if (vcsManager.getVcsFor(root)?.name == JujutsuVcs.VCS_NAME) {
                repositories.computeIfAbsent(root) { JjRepository(project, it) }
            }
        }
        return repositories.values.toList()
    }

    fun invalidate(root: VirtualFile) {
        repositories.remove(root)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): JjRepositoryManager = project.service()
    }
}
