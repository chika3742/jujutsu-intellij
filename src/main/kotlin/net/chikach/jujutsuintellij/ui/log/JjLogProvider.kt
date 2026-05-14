package net.chikach.jujutsuintellij.ui.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsLogRefresher
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsUser
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.cli.JjJsonDecoders
import net.chikach.jujutsuintellij.cli.LogEntryJson
import net.chikach.jujutsuintellij.cli.template.JjTemplates
import net.chikach.jujutsuintellij.cli.template.email
import net.chikach.jujutsuintellij.cli.template.name
import net.chikach.jujutsuintellij.cli.template.obj
import net.chikach.jujutsuintellij.cli.template.serializableTemplateExpr
import net.chikach.jujutsuintellij.cli.template.string
import net.chikach.jujutsuintellij.cli.template.timestamp
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

class JjLogProvider(private val project: Project) : VcsLogProvider {

    private val refManager = JjLogRefManager()

    private val logEntryTemplate: String by lazy {
        JjTemplates.commitJsonLine {
            obj {
                "ci" to string(commitId)
                "ch" to string(changeId)
                "p" to string(serializableTemplateExpr("self.parents().map(|p| p.commit_id()).join(\" \")"))
                "an" to string(author.name())
                "ae" to string(author.email())
                "at" to string(author.timestamp())
                "d" to string(description)
            }
        }
    }

    private val timedCommitTemplate: String by lazy {
        JjTemplates.commitJsonLine {
            obj {
                "ci" to string(commitId)
                "p" to string(serializableTemplateExpr("self.parents().map(|p| p.commit_id()).join(\" \")"))
                "at" to string(author.timestamp())
            }
        }
    }

    override fun readFirstBlock(root: VirtualFile, requirements: VcsLogProvider.Requirements): VcsLogProvider.DetailedLogData {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        val count = requirements.commitCount
        val objects = JjCommands.getInstance().recentLog(repo, count, logEntryTemplate)
        val entries = JjJsonDecoders.decodeLogEntries(objects)
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

        val objects = JjCommands.getInstance().allLog(repo, timedCommitTemplate)
        val users = mutableSetOf<VcsUser>()
        JjJsonDecoders.decodeTimedCommits(objects).forEach { entry ->
            commitConsumer.consume(
                factory.createTimedCommit(
                    factory.createHash(entry.commitId),
                    entry.parentIds.map { factory.createHash(it) },
                    entry.time.time,
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

        val objects = JjCommands.getInstance().logByIds(repo, hashes, logEntryTemplate)
        JjJsonDecoders.decodeLogEntries(objects).forEach { entry ->
            consumer.consume(entry.toCommitMetadata(root, factory))
        }
    }

    override fun readFullDetails(root: VirtualFile, hashes: List<String>, commitConsumer: Consumer<in VcsFullCommitDetails>) {
        if (hashes.isEmpty()) return
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val factory = project.service<VcsLogObjectsFactory>()

        val objects = JjCommands.getInstance().logByIds(repo, hashes, logEntryTemplate)
        JjJsonDecoders.decodeLogEntries(objects).forEach { entry ->
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
        val nameResult = JjCommands.getInstance().configGet(repo, "user.name")
        val emailResult = JjCommands.getInstance().configGet(repo, "user.email")
        if (!nameResult.isSuccess) return null
        return factory.createUser(nameResult.stdout.trim(), emailResult.stdout.trim())
    }

    override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> = emptyList()

    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? = null

    override fun getCurrentBranch(root: VirtualFile): String? = null

    private fun LogEntryJson.toCommitMetadata(root: VirtualFile, factory: VcsLogObjectsFactory): VcsCommitMetadata {
        val hash = factory.createHash(commitId)
        val parents = parentIds.map { factory.createHash(it) }
        val subject = description.lines().firstOrNull()?.trim() ?: ""
        return factory.createCommitMetadata(
            hash, parents, authorTime.time, root,
            subject, authorName, authorEmail,
            description.trimEnd('\n'),
            authorName, authorEmail, authorTime.time,
        )
    }

    private fun loadBookmarkRefs(root: VirtualFile, factory: VcsLogObjectsFactory): List<VcsRef> {
        val repo = JjRepositoryManager.getInstance(project).getRepositoryForRoot(root)
        val result = JjCommands.getInstance().bookmarkCommitsForLog(repo)
        if (!result.isSuccess) return emptyList()

        return result.stdout.lineSequence()
            .filter { it.isNotBlank() }
            .flatMap { line ->
                val parts = line.split("\t")
                if (parts.size < 2) return@flatMap emptySequence()
                val commitId = parts[0].trim().takeIf { it.isNotBlank() } ?: return@flatMap emptySequence()
                val hash = factory.createHash(commitId)
                parts.drop(1).asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { name -> factory.createRef(hash, name, JjBookmarkRefType, root) }
            }
            .toList()
    }
}
