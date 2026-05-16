package net.chikach.jujutsuintellij.ui.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.*
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.repo.model.JjCommit
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

class JjLogProvider(private val project: Project) : VcsLogProvider {

    private val refManager = JjLogRefManager()

    override fun readFirstBlock(root: VirtualFile, requirements: VcsLogProvider.Requirements): VcsLogProvider.DetailedLogData {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        val entries = repo.recentLog(requirements.commitCount)
        val refs = loadBookmarkRefs(root, factory)
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
        repo.allTimedCommits().forEach { entry ->
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

        repo.logByIds(hashes).forEach { entry ->
            consumer.consume(entry.toCommitMetadata(root, factory))
        }
    }

    override fun readFullDetails(root: VirtualFile, hashes: List<String>, commitConsumer: Consumer<in VcsFullCommitDetails>) {
        if (hashes.isEmpty()) return
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        repo.logByIds(hashes).forEach { entry ->
            val metadata = entry.toCommitMetadata(root, factory)
            commitConsumer.consume(object : VcsFullCommitDetails, VcsCommitMetadata by metadata {
                override fun getChanges(): Collection<Change> = emptyList()
                override fun getChanges(parent: Int): Collection<Change> = emptyList()
            })
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

    override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> = emptyList()

    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? = null

    override fun getCurrentBranch(root: VirtualFile): String? = null

    private fun JjCommit.toCommitMetadata(root: VirtualFile, factory: VcsLogObjectsFactory): VcsCommitMetadata {
        val hash = factory.createHash(commitId)
        val parents = parentIds.map { factory.createHash(it) }
        val placeholder = when {
            isRoot -> ROOT_PLACEHOLDER
            description.isBlank() -> NO_DESCRIPTION_PLACEHOLDER
            else -> null
        }
        val subject = placeholder ?: (description.lines().firstOrNull()?.trim() ?: "")
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
        return repo.bookmarksForLog().map { ref ->
            factory.createRef(factory.createHash(ref.commitId), ref.name, JjBookmarkRefType, root)
        }
    }

    companion object {
        const val NO_DESCRIPTION_PLACEHOLDER = "<no description set>"
        const val ROOT_PLACEHOLDER = "<root>"
    }
}
