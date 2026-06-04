package net.chikach.jujutsuintellij.caches

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.model.JjCommitRef

/**
 * Project-scoped cache of bookmark refs across all Jujutsu roots, kept fresh by
 * [net.chikach.jujutsuintellij.repo.JjChangeWatcher]. The toolbar bookmark popup reads it
 * synchronously so opening the popup never runs `jj` on the EDT.
 *
 * Each [Entry] holds one repository's bookmark refs (local + remote), excluding the internal
 * `git` pseudo-remote, so the popup can offer track / untrack per remote bookmark.
 */
@Service(Service.Level.PROJECT)
class JjBookmarkCache(private val project: Project) {

    /** A repository together with its bookmark refs (local and non-`git` remote). */
    data class Entry(val repo: JjRepository, val refs: List<JjCommitRef>)

    @Volatile
    var entries: List<Entry> = emptyList()
        private set

    /** Reloads the bookmark list. Runs the `jj` CLI, so call from a background thread only. */
    fun reload() {
        val repos = runReadAction { JjRepositoryManager.getInstance(project).getAll() }
        entries = repos.mapNotNull { repo ->
            runCatching {
                // No revset: `jj bookmark list -r <revset>` filters by *local* target, which drops
                // remote-only bookmarks (those without a local counterpart). `--all-remotes` alone
                // lists every local and remote bookmark.
                val refs = repo.listBookmarks(
                    sortByCommitterDate = true,
                    allRemotes = true,
                ).filter { it.remote != JjRepository.INTERNAL_GIT_REMOTE }
                Entry(repo, refs)
            }.getOrNull()
        }
    }

    companion object {
        fun getInstance(project: Project): JjBookmarkCache = project.service()
    }
}
