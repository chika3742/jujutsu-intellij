package net.chikach.jujutsuintellij.caches

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import net.chikach.jujutsuintellij.repo.model.JjCommit
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class JjCommitCache {

    data class Info(val description: String, val isRoot: Boolean)

    private val infos = ConcurrentHashMap<String, Info>()

    fun record(commits: List<JjCommit>) {
        commits.forEach {
            record(it)
        }
    }

    fun record(commit: JjCommit) {
        infos[commit.commitId] = Info(commit.description, commit.isRoot)
    }

    fun get(hash: String): Info? =
        infos[hash]

    companion object {
        fun getInstance(project: Project): JjCommitCache = project.service()
    }
}
