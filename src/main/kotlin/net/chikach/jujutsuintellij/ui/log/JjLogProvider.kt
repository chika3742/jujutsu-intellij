package net.chikach.jujutsuintellij.ui.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.*
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjCommitCache
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.caches.JjWorkingCopyCache
import net.chikach.jujutsuintellij.repo.model.JjCommit
import net.chikach.jujutsuintellij.repo.model.JjFileChange
import net.chikach.jujutsuintellij.vcs.JjConflictTracker
import net.chikach.jujutsuintellij.vcs.JjContentRevision
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

class JjLogProvider(private val project: Project) : VcsLogProvider {

    private val refManager = JjLogRefManager()

    override fun readFirstBlock(root: VirtualFile, requirements: VcsLogProvider.Requirements): VcsLogProvider.DetailedLogData {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        val entries = repo.recentLog(requirements.commitCount)
        JjConflictTracker.getInstance(project).record(entries)
        val refs = loadBookmarkRefs(root, factory)
        JjCommitCache.getInstance(project).record(entries)
        val commitsList = entries.map { entry -> entry.toCommitMetadata(root, factory) }

        return object : VcsLogProvider.DetailedLogData {
            override fun getCommits(): List<VcsCommitMetadata> = commitsList
            override fun getRefs(): Set<VcsRef> = refs.toSet()
        }
    }

    override fun readAllHashes(root: VirtualFile, commitConsumer: Consumer<in TimedVcsCommit>): VcsLogProvider.LogData {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        val users = mutableSetOf<VcsUser>()
        val entries = repo.allTimedCommits()
        JjConflictTracker.getInstance(project).record(entries)
        entries.forEach { entry ->
            commitConsumer.consume(
                factory.createTimedCommit(
                    factory.createHash(entry.commitId),
                    entry.parentIds.map { factory.createHash(it) },
                    entry.authorTime.time,
                )
            )
        }

        val refs = loadBookmarkRefs(root, factory)
        return object : VcsLogProvider.LogData {
            override fun getRefs(): Set<VcsRef> = refs.toSet()
            override fun getUsers(): Set<VcsUser> = users
        }
    }

    override fun readMetadata(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsCommitMetadata>) {
        if (hashes.isEmpty()) return
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        val entries = repo.logByIds(hashes)
        JjConflictTracker.getInstance(project).record(entries)
        entries.forEach { entry ->
            consumer.consume(entry.toCommitMetadata(root, factory))
            JjCommitCache.getInstance(project).record(entry)
        }
    }

    override fun readFullDetails(root: VirtualFile, hashes: List<String>, commitConsumer: Consumer<in VcsFullCommitDetails>) {
        if (hashes.isEmpty()) return
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        val entries = repo.logByIds(hashes)
        JjConflictTracker.getInstance(project).record(entries)
        entries.forEach { entry ->
            val metadata = entry.toCommitMetadata(root, factory)
            // Compute diffs here (readFullDetails runs off-EDT); getChanges() is later queried on
            // the EDT by the log's diff preview and must not shell out to jj itself.
            val changesByParent = entry.parentIds.map { changesBetween(repo, it, entry.commitId) }
            commitConsumer.consume(object : VcsFullCommitDetails, VcsCommitMetadata by metadata {
                override fun getChanges(): Collection<Change> = changesByParent.firstOrNull().orEmpty()
                override fun getChanges(parent: Int): Collection<Change> = changesByParent.getOrNull(parent).orEmpty()
            })
        }
    }

    /** Builds [Change]s for the diff of [toRev] against [fromRev] (commit-vs-parent in the log). */
    private fun changesBetween(repo: JjRepository, fromRev: String, toRev: String): List<Change> =
        repo.diffSummary(fromRev, toRev).map { fc ->
            fun before(path: String) = JjContentRevision(repo, path, fromRev)
            fun after(path: String) = JjContentRevision(repo, path, toRev)
            when (fc.status) {
                JjFileChange.Status.ADDED -> Change(null, after(fc.path), FileStatus.ADDED)
                JjFileChange.Status.DELETED -> Change(before(fc.path), null, FileStatus.DELETED)
                JjFileChange.Status.MODIFIED -> Change(before(fc.path), after(fc.path), FileStatus.MODIFIED)
                JjFileChange.Status.RENAMED -> Change(before(fc.sourcePath!!), after(fc.path), FileStatus.MODIFIED)
                JjFileChange.Status.COPIED -> Change(before(fc.sourcePath!!), after(fc.path), FileStatus.ADDED)
            }
        }

    override fun getSupportedVcs(): VcsKey = JujutsuVcs.KEY

    override fun getReferenceManager(): VcsLogRefManager = refManager

    override fun subscribeToRootRefreshEvents(
        roots: Collection<VirtualFile>,
        refresher: VcsLogRefresher,
    ): Disposable = Disposable {}

    override fun getCurrentUser(root: VirtualFile): VcsUser? {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()
        val user = repo.currentUser() ?: return null
        return factory.createUser(user.name, user.email)
    }

    override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        return repo.listBookmarksByCommitId(commitHash.asString()).map { it.name }
    }

    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? = null

    override fun getCurrentBranch(root: VirtualFile): String? {
        // Invoked on the EDT by the platform's CurrentBranchHighlighter, so it must not run jj
        // synchronously. Return the cached value and schedule a debounced background refresh.
        val cache = JjWorkingCopyCache.getInstance(project)
        cache.refresh()
        return cache.currentBranch
    }

    private fun JjCommit.toCommitMetadata(root: VirtualFile, factory: VcsLogObjectsFactory): VcsCommitMetadata {
        val hash = factory.createHash(commitId)
        val parents = parentIds.map { factory.createHash(it) }
        val placeholder = when {
            isRoot -> JujutsuBundle.message("changeDesc.root")
            description.isBlank() -> JujutsuBundle.message("changeDesc.noDescriptionSet")
            else -> null
        }
        var subject = placeholder ?: (description.lines().firstOrNull()?.trim() ?: "")
        if (isConflicted) {
            subject += " ${JujutsuBundle.message("changeDesc.conflicted")}"
        }
        val message = placeholder ?: description.trimEnd('\n')
        return factory.createCommitMetadata(
            hash, parents, authorTime.time, root,
            subject, authorName, authorEmail,
            message,
            authorName, authorEmail, authorTime.time,
        )
    }

    private fun loadBookmarkRefs(root: VirtualFile, factory: VcsLogObjectsFactory): List<VcsRef> {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        return repo.listBookmarks(revset = "bookmarks()")
            .mapNotNull { ref ->
                val commitId = ref.commitId ?: return@mapNotNull null
                factory.createRef(factory.createHash(commitId), ref.name, JjBookmarkRefType, root)
            }
    }
}
