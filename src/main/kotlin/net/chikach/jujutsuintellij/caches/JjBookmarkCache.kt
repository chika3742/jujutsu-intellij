package net.chikach.jujutsuintellij.caches

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager

/**
 * Project-scoped cache of local bookmarks across all Jujutsu roots, kept fresh by
 * [net.chikach.jujutsuintellij.repo.JjChangeWatcher]. The toolbar bookmark popup reads it
 * synchronously so opening the popup never runs `jj` on the EDT.
 */
@Service(Service.Level.PROJECT)
class JjBookmarkCache(private val project: Project) {

    /** A local bookmark together with the repository that owns it. */
    data class Entry(val repo: JjRepository, val name: String)

    @Volatile
    var entries: List<Entry> = emptyList()
        private set

    /** Reloads the bookmark list. Runs the `jj` CLI, so call from a background thread only. */
    fun reload() {
        val repos = runReadAction { JjRepositoryManager.getInstance(project).getAll() }
        entries = repos.flatMap { repo ->
            runCatching {
                repo.listBookmarks(revset = "bookmarks()", sortByCommitterDate = true)
                    .filter { it.isLocal }
                    .map { it.name }
                    .distinct()
                    .map { Entry(repo, it) }
            }.getOrDefault(emptyList())
        }
    }

    companion object {
        fun getInstance(project: Project): JjBookmarkCache = project.service()
    }
}
