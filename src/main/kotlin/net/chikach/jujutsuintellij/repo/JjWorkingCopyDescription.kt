package net.chikach.jujutsuintellij.repo

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import net.chikach.jujutsuintellij.cli.JjCommands
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Project-scoped cache for the working-copy commit's description (`jj log -r @ -T description`).
 *
 * [refresh] debounces requests by 200 ms and executes on the application's shared pooled executor.
 * The background task:
 *   1. Acquires a read action to resolve the repository (required by WorkspaceFileIndex).
 *   2. Releases the read action and then runs the jj CLI (not allowed inside a read action).
 *   3. Updates [description] and notifies listeners on the EDT when the value changes.
 */
@Service(Service.Level.PROJECT)
class JjWorkingCopyDescription(private val project: Project) : Disposable {

    @Volatile var description: String = ""
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

    /** Debounce-schedules a background refresh (200 ms). Safe to call from any thread. */
    @Synchronized
    fun refresh() {
        pendingRefresh?.cancel(false)
        pendingRefresh = AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(::doRefresh, 200, TimeUnit.MILLISECONDS)
    }

    private fun doRefresh() {
        // Step 1: resolve repository inside a read action (WorkspaceFileIndex requirement).
        val repo = runReadAction {
            JjRepositoryManager.getInstance(project).getAll().firstOrNull()
        } ?: return

        // Step 2: run jj CLI outside the read action (process execution not allowed inside one).
        val result = runCatching { JjCommands.getInstance().getDescription(repo) }.getOrNull()
        if (result != null && result.isSuccess) {
            val newDesc = result.stdout.trimEnd()
            if (newDesc != description) {
                description = newDesc
                ActivityTracker.getInstance().inc()
                ApplicationManager.getApplication().invokeLater {
                    changeListeners.forEach { it.run() }
                }
            }
        }
    }

    @Synchronized
    override fun dispose() {
        pendingRefresh?.cancel(false)
        pendingRefresh = null
    }

    companion object {
        fun getInstance(project: Project): JjWorkingCopyDescription = project.service()
    }
}
