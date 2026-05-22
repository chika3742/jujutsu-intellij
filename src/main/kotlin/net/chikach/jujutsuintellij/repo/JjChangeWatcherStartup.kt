package net.chikach.jujutsuintellij.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager

/**
 * Instantiates [JjChangeWatcher] (registering its VFS listener) and starts watching the
 * `.jj/repo/op_heads` directories once the VCS mappings are available.
 */
class JjChangeWatcherStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val watcher = JjChangeWatcher.getInstance(project)
        ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
            watcher.startWatching()
        }
    }
}