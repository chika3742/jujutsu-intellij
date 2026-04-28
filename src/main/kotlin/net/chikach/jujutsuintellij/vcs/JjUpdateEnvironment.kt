package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.Service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.update.SequentialUpdatesContext
import com.intellij.openapi.vcs.update.UpdateEnvironment
import com.intellij.openapi.vcs.update.UpdateSession
import com.intellij.openapi.vcs.update.UpdatedFiles
import net.chikach.jujutsuintellij.cli.JjCommands
import net.chikach.jujutsuintellij.repo.JjRepositoryManager

/**
 * Implements "Update Project" (VCS > Update) via `jj git fetch`.
 * Only meaningful for co-located / git-backed repositories.
 */
@Service(Service.Level.PROJECT)
class JjUpdateEnvironment(private val project: Project) : UpdateEnvironment {

    override fun fillGroups(updatedFiles: UpdatedFiles) {}

    override fun createConfigurable(roots: Collection<FilePath>): Configurable? = null

    override fun validateOptions(roots: Collection<FilePath>): Boolean = true

    override fun updateDirectories(
        contentRoots: Array<out FilePath>,
        updatedFiles: UpdatedFiles,
        progressIndicator: ProgressIndicator,
        context: Ref<SequentialUpdatesContext>,
    ): UpdateSession {
        val exceptions = mutableListOf<VcsException>()
        val repos = JjRepositoryManager.getInstance(project).getAll()
        val commands = JjCommands.getInstance()

        for (repo in repos) {
            if (progressIndicator.isCanceled) break
            progressIndicator.text = "Fetching from remote…"
            val result = commands.gitFetch(repo)
            if (!result.isSuccess) {
                exceptions += VcsException("jj git fetch failed: ${result.stderr.trim()}")
            }
        }

        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()

        return object : UpdateSession {
            override fun getExceptions(): List<VcsException> = exceptions
            override fun isCanceled(): Boolean = progressIndicator.isCanceled
            override fun onRefreshFilesCompleted() {}
        }
    }
}
