package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import net.chikach.jujutsuintellij.repo.model.JjCommit
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-scoped set of commit hashes known to contain merge conflicts. Populated by
 * [net.chikach.jujutsuintellij.ui.log.JjLogProvider] from `JjCommit.isConflicted` whenever
 * commits are fetched; consulted by the log highlighter and the resolve action.
 */
@Service(Service.Level.PROJECT)
class JjConflictTracker {

    private val conflicted = ConcurrentHashMap.newKeySet<String>()

    fun record(commits: Collection<JjCommit>) {
        commits.forEach {
            if (it.isConflicted) conflicted.add(it.commitId) else conflicted.remove(it.commitId)
        }
    }

    fun isConflicted(hash: String): Boolean = hash in conflicted

    companion object {
        fun getInstance(project: Project): JjConflictTracker = project.service()
    }
}
