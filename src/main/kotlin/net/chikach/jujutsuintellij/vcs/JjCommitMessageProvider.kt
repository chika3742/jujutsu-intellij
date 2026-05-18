package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.DelayedCommitMessageProvider
import net.chikach.jujutsuintellij.repo.JjWorkingCopyCache

/**
 * Pre-populates and live-syncs the Commit tool window message field with the current `@`'s
 * description.
 *
 * - [getCommitMessage] supplies the initial value when the panel opens.
 * - [init] subscribes to [JjWorkingCopyCache] and overwrites the field whenever the
 *   description changes externally (e.g. after the user runs "Describe" from the toolbar widget
 *   or commits via the panel itself).
 */
class JjCommitMessageProvider : DelayedCommitMessageProvider {

    override fun getCommitMessage(forChangelist: LocalChangeList, project: Project): String? {
        if (!JujutsuVcs.isActiveIn(project)) return null
        return JjWorkingCopyCache.getInstance(project).description.ifEmpty { null }
    }

    override fun init(project: Project, commitUi: CommitMessageUi, disposable: Disposable) {
        if (!JujutsuVcs.isActiveIn(project)) return

        val descService = JjWorkingCopyCache.getInstance(project)
        val removeListener = descService.addChangeListener {
            commitUi.setText(descService.description)
        }
        Disposer.register(disposable) { removeListener.run() }

        // Pull the latest value once on open in case the cache is stale.
        descService.refresh()
    }
}
