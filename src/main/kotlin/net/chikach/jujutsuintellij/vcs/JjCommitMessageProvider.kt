package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.DelayedCommitMessageProvider
import net.chikach.jujutsuintellij.caches.JjWorkingCopyCache
import net.chikach.jujutsuintellij.repo.JjChangeWatcher
import net.chikach.jujutsuintellij.repo.JjRepositoryManager
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Two-way syncs the Commit tool window message field with the current `@`'s description.
 *
 * - [getCommitMessage] supplies the initial value when the panel opens.
 * - [init] keeps both directions in sync:
 *   - `@` → field: subscribes to [JjWorkingCopyCache] and overwrites the field whenever the
 *     description changes externally (e.g. after the user runs "Describe" from the toolbar widget).
 *   - field → `@`: debounces the user's typing and reflects it onto the working copy via
 *     `jj describe`, so the field effectively edits `@`'s description live.
 */
class JjCommitMessageProvider : DelayedCommitMessageProvider {

    override fun getCommitMessage(forChangelist: LocalChangeList, project: Project): String? {
        if (!JujutsuVcs.isActiveIn(project)) return null
        return JjWorkingCopyCache.getInstance(project).description.ifEmpty { null }
    }

    override fun init(project: Project, commitUi: CommitMessageUi, disposable: Disposable) {
        if (!JujutsuVcs.isActiveIn(project)) return

        val cache = JjWorkingCopyCache.getInstance(project)

        // `@` → field: overwrite only on genuine external changes, preserving the caret otherwise.
        // Skipping the write when the value already matches avoids clobbering the caret while the
        // user is typing (the field-driven describe below echoes back through this cache).
        val removeListener = cache.addChangeListener {
            if (cache.description != commitUi.text.trimEnd()) {
                commitUi.setText(cache.description)
            }
        }
        Disposer.register(disposable) { removeListener.run() }

        // field → `@`: debounce typing and reflect it onto the working copy via `jj describe`.
        // Per-panel state is held in a dedicated object so concurrent panels do not share it
        // (the provider itself is a single application-level extension instance).
        val commitMessage = commitUi as? CommitMessage
        if (commitMessage != null) {
            val describer = LiveDescriber(project)
            commitMessage.editorField.document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    describer.schedule(event.document.text)
                }
            }, disposable)
            Disposer.register(disposable) { describer.cancel() }
        }

        // Pull the latest value once on open in case the cache is stale.
        cache.refresh()
    }

    /** Debounces commit-message edits and applies them to `@` via `jj describe`. */
    private class LiveDescriber(private val project: Project) {

        private val pendingText = AtomicReference("")

        @Volatile
        private var pendingDescribe: ScheduledFuture<*>? = null

        /** Records the latest text and (re)schedules a debounced describe. Call on the EDT. */
        fun schedule(text: String) {
            pendingText.set(text)
            pendingDescribe?.cancel(false)
            pendingDescribe = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(::applyDescribe, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
        }

        fun cancel() {
            pendingDescribe?.cancel(false)
            pendingDescribe = null
        }

        private fun applyDescribe() {
            // Resolve the repository inside a read action (WorkspaceFileIndex requirement), then
            // run the CLI outside it. Mirrors JjWorkingCopyCache's single-`@` model.
            val repo = runReadAction {
                JjRepositoryManager.getInstance(project).getAll().firstOrNull()
            } ?: return

            val text = pendingText.get().trimEnd()
            // Skip when `@` already carries this description: covers the initial population, our own
            // setText, and external-describe echoes — avoiding redundant CLI calls and feedback loops.
            if (text == JjWorkingCopyCache.getInstance(project).description) return

            runCatching { repo.describe(text) }
                .onSuccess { JjChangeWatcher.getInstance(project).forceRefresh() }
                .onFailure { thisLogger().warn("Live describe of working copy failed", it) }
        }

        companion object {
            private const val DEBOUNCE_MS = 500L
        }
    }
}
