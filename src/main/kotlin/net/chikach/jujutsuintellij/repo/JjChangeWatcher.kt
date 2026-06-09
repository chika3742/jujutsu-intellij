package net.chikach.jujutsuintellij.repo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import net.chikach.jujutsuintellij.caches.JjBookmarkCache
import net.chikach.jujutsuintellij.caches.JjWorkingCopyCache
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Refreshes the VCS Log, working-copy cache, and Local Changes when a Jujutsu operation occurs.
 *
 * Jujutsu records every operation (commit, `new`, rebase, abandon, …, from any source — the plugin,
 * an embedded terminal, or an external terminal) by writing a new op head under
 * `.jj/repo/op_heads/heads`. That directory is the universal change signal.
 *
 * `.jj` is excluded content, so the platform doesn't load its subtree into the VFS on its own and
 * therefore delivers no events for it. Mirroring git4idea's `GitRepositoryUpdater`, [startWatching]
 * adds a native watch on `.jj/repo/op_heads` and eagerly loads that subtree into the VFS
 * ([VfsUtilCore.processFilesRecursively]); once loaded, the native watcher delivers events for it to
 * [JjVfsListener], independent of IDE focus, so embedded-terminal operations are caught too.
 * Plugin-initiated operations additionally call [forceRefresh] for an immediate update rather than
 * waiting for the (slightly delayed) native-watcher event.
 *
 * Refreshing the log runs the platform's incremental join, which drops commits by diffing the
 * registered refs (previousRefs − newRefs). jj rewrites commit ids (`new`/rebase/squash/abandon), so
 * [net.chikach.jujutsuintellij.ui.log.JjLogProvider] registers every visible head (`heads(all())`,
 * including `@`) as a ref — analogous to git's HEAD — so the joiner can remove the rewritten/abandoned
 * nodes. Cases the joiner cannot reconcile fall back to a full reload automatically.
 */
@Service(Service.Level.PROJECT)
class JjChangeWatcher(private val project: Project) : Disposable {

    private val watchRequests = CopyOnWriteArrayList<LocalFileSystem.WatchRequest>()

    @Volatile private var pendingRefresh: ScheduledFuture<*>? = null
    @Volatile private var disposed = false

    init {
        VirtualFileManager.getInstance().addAsyncFileListener(JjVfsListener(), this)
    }

    /**
     * Watches and eagerly loads each `.jj/repo/op_heads` subtree so the native watcher delivers VFS
     * events for it. Call after the VCS mappings are initialized.
     */
    fun startWatching() {
        val lfs = LocalFileSystem.getInstance()
        val opHeadsPaths = runReadAction {
            JjRepositoryManager.getInstance(project).getAll().map { "${it.rootPath}/$JJ_OP_HEADS_PATH" }
        }
        if (opHeadsPaths.isEmpty()) return

        watchRequests += lfs.addRootsToWatch(opHeadsPaths.toSet(), true)
        for (path in opHeadsPaths) {
            val dir = lfs.refreshAndFindFileByPath(path) ?: continue
            VfsUtilCore.processFilesRecursively(dir) { true }
        }
    }

    /** Schedules a refresh immediately (used after plugin-initiated jj operations). */
    fun forceRefresh() = scheduleRefresh()

    @Synchronized
    private fun scheduleRefresh() {
        if (disposed) return
        pendingRefresh?.cancel(false)
        pendingRefresh = AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(::doRefresh, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun doRefresh() {
        val roots = runReadAction {
            JjRepositoryManager.getInstance(project).getAll().map { it.root }
        }
        if (roots.isNotEmpty()) {
            VcsProjectLog.getInstance(project).dataManager?.refresh(roots)
        }
        JjWorkingCopyCache.getInstance(project).refresh()
        JjBookmarkCache.getInstance(project).reload()
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        pendingRefresh?.cancel(false)
        pendingRefresh = null
        LocalFileSystem.getInstance().removeWatchedRoots(watchRequests)
        watchRequests.clear()
    }

    /** Off-EDT VFS listener: schedules a refresh when an op head under `.jj/repo/op_heads` changes. */
    private inner class JjVfsListener : AsyncFileListener {
        override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
            if (events.none { it.path.contains(JJ_OP_HEADS_SEGMENT) }) return null
            return object : AsyncFileListener.ChangeApplier {
                override fun afterVfsChange() = scheduleRefresh()
            }
        }
    }

    companion object {
        private const val JJ_OP_HEADS_PATH = ".jj/repo/op_heads"
        private const val JJ_OP_HEADS_SEGMENT = "/$JJ_OP_HEADS_PATH/"
        private const val DEBOUNCE_MS = 300L

        fun getInstance(project: Project): JjChangeWatcher = project.service()
    }
}
