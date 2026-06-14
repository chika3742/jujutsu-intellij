package net.chikach.jujutsuintellij.caches

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Project-scoped cache for working-copy (`@`) state: its commit id, description, and the nearest
 * ancestor bookmark ("current branch").
 *
 * [refresh] debounces requests by 200 ms and executes on the application's shared pooled executor.
 * The background task:
 *   1. Acquires a read action to resolve the repository (required by WorkspaceFileIndex).
 *   2. Releases the read action and then runs the jj CLI (not allowed inside a read action).
 *   3. Updates [description] / [currentBranch] and notifies listeners on the EDT when a value
 *      changes.
 *
 * [currentBranch] in particular is read from the EDT by the VCS log's `CurrentBranchHighlighter`;
 * caching it here keeps that call non-blocking instead of running `jj` synchronously on the EDT.
 */
@Service(Service.Level.PROJECT)
class JjWorkingCopyCache(private val project: Project) : Disposable {

    @Volatile var description: String = ""
        private set

    @Volatile var commitId: String? = null
        private set

    @Volatile var currentBranch: String? = null
        private set

    private val changeListeners = CopyOnWriteArrayList<Runnable>()

    @Volatile private var pendingRefresh: ScheduledFuture<*>? = null

    /**
     * Registers [listener] to be called on the EDT whenever [description] changes.
     * Returns a [Runnable] that unregisters the listener when invoked.
     */
    fun addChangeListener(listener: Runnable): Runnable {
        changeListeners.add(listener)
        return Runnable { changeListeners.remove(listener) }
    }

    /**
     * Debounce-schedules a background refresh (200 ms). Safe to call from any thread.
     *
     * Idempotent while a refresh is already scheduled: high-frequency callers (e.g. the BGT
     * `JjRevisionWidget.update` re-renders) would otherwise reset the trailing-edge timer
     * indefinitely and starve [doRefresh].
     */
    @Synchronized
    fun refresh() {
        val current = pendingRefresh
        if (current != null && !current.isDone) return
        pendingRefresh = AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(::doRefresh, 200, TimeUnit.MILLISECONDS)
    }

    private fun doRefresh() {
        // Step 1: resolve repository inside a read action (WorkspaceFileIndex requirement).
        val repo = runReadAction {
            JjRepositoryManager.getInstance(project).getAll().firstOrNull()
        } ?: return

        // Step 2: run jj CLI outside the read action (process execution not allowed inside one).
        var changed = false

        // One `jj log -r @` call yields both the description and the commit id of the working copy.
        runCatching { repo.workingCopyCommit() }.getOrNull()?.let { wc ->
            val newDesc = wc.description.trimEnd()
            if (newDesc != description) {
                description = newDesc
                changed = true
            }
            if (wc.commitId != commitId) {
                commitId = wc.commitId
                changed = true
            }
        }

        runCatching { repo.currentBranch() }.onSuccess { newBranch ->
            if (newBranch != currentBranch) {
                currentBranch = newBranch
                changed = true
            }
        }

        if (changed) {
            ActivityTracker.getInstance().inc()
            ApplicationManager.getApplication().invokeLater {
                changeListeners.forEach { it.run() }
            }
        }
    }

    @Synchronized
    override fun dispose() {
        pendingRefresh?.cancel(false)
        pendingRefresh = null
    }

    companion object {
        fun getInstance(project: Project): JjWorkingCopyCache = project.service()
    }
}
