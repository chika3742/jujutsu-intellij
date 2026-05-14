package net.chikach.jujutsuintellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.json.JsonObject
import net.chikach.jujutsuintellij.repo.JjRepository
import java.nio.file.Path

@Service(Service.Level.APP)
class JjCommands {

    fun version(workDir: Path): JjCommandResult =
        execute(JjCommandFactory.version(workDir))

    fun diffSummary(
        repo: JjRepository,
        fromRef: String,
        toRef: String,
    ): JjCommandResult =
        execute(
            JjCommandFactory.diffSummary(
                workDir = repo.rootPathNio,
                fromRef = fromRef,
                toRef = toRef,
            )
        )

    fun fileHistory(
        repo: JjRepository,
        relativePath: String,
        template: String,
        revset: String = DEFAULT_LOG_REVSET,
    ): List<JsonObject> =
        executeObjects(
            JjCommandFactory.fileHistory(
                workDir = repo.rootPathNio,
                revset = revset,
                template = template,
                relativePath = repo.normalizeRelativePath(relativePath),
            )
        )

    fun annotateFile(
        repo: JjRepository,
        relativePath: String,
        template: String,
        revision: String? = null,
    ): List<JsonObject> =
        executeObjects(
            JjCommandFactory.annotateFile(
                workDir = repo.rootPathNio,
                template = template,
                relativePath = repo.normalizeRelativePath(relativePath),
                revision = revision,
            )
        )

    fun showFile(
        repo: JjRepository,
        revision: String,
        relativePath: String,
    ): JjCommandResult =
        execute(
            JjCommandFactory.showFile(
                workDir = repo.rootPathNio,
                revision = revision,
                relativePath = repo.normalizeRelativePath(relativePath),
            )
        )

    fun describe(repo: JjRepository, message: String): JjCommandResult =
        execute(JjCommandFactory.describe(repo.rootPathNio, message))

    fun newChange(repo: JjRepository): JjCommandResult =
        execute(JjCommandFactory.newChange(repo.rootPathNio))

    fun restore(repo: JjRepository, fromRevision: String, relativePaths: List<String>): JjCommandResult =
        execute(JjCommandFactory.restore(repo.rootPathNio, fromRevision, relativePaths))

    fun abandon(repo: JjRepository, revset: String): JjCommandResult =
        execute(JjCommandFactory.abandon(repo.rootPathNio, revset))

    fun getDescription(repo: JjRepository): JjCommandResult =
        execute(JjCommandFactory.getDescription(repo.rootPathNio))

    fun recentLog(repo: JjRepository, count: Int, template: String): List<JsonObject> =
        executeObjects(JjCommandFactory.recentLog(repo.rootPathNio, count, template))

    fun allLog(repo: JjRepository, template: String): List<JsonObject> =
        executeObjects(JjCommandFactory.allLog(repo.rootPathNio, template))

    fun logByIds(repo: JjRepository, commitIds: List<String>, template: String): List<JsonObject> =
        executeObjects(JjCommandFactory.logByIds(repo.rootPathNio, commitIds, template))

    fun bookmarkList(repo: JjRepository): JjCommandResult =
        execute(JjCommandFactory.bookmarkList(repo.rootPathNio))

    fun configGet(repo: JjRepository, key: String): JjCommandResult =
        execute(JjCommandFactory.configGet(repo.rootPathNio, key))

    fun bookmarkCreate(repo: JjRepository, name: String, revision: String = "@"): JjCommandResult =
        execute(JjCommandFactory.bookmarkCreate(repo.rootPathNio, name, revision))

    fun bookmarkDelete(repo: JjRepository, name: String): JjCommandResult =
        execute(JjCommandFactory.bookmarkDelete(repo.rootPathNio, name))

    fun bookmarkSet(repo: JjRepository, name: String, revision: String = "@"): JjCommandResult =
        execute(JjCommandFactory.bookmarkSet(repo.rootPathNio, name, revision))

    fun gitFetch(repo: JjRepository, remote: String? = null): JjCommandResult =
        execute(JjCommandFactory.gitFetch(repo.rootPathNio, remote))

    fun gitPush(repo: JjRepository, bookmark: String? = null, remote: String? = null): JjCommandResult =
        execute(JjCommandFactory.gitPush(repo.rootPathNio, bookmark, remote))

    fun bookmarkCommitsForLog(repo: JjRepository): JjCommandResult =
        execute(JjCommandFactory.bookmarkCommitsForLog(repo.rootPathNio))

    private fun execute(request: JjCli.Request): JjCommandResult =
        JjCli.getInstance().execute(request)

    private fun executeObjects(request: JjCli.Request): List<JsonObject> =
        JjJsonCommand.getInstance().executeObjects(request)

    companion object {
        private const val DEFAULT_LOG_REVSET = "::@"

        @JvmStatic
        fun getInstance(): JjCommands = service()
    }
}
