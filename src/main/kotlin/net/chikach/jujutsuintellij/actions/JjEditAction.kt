package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.caches.JjWorkingCopyCache

/** Moves the working copy to the commit selected in the VCS Log (`jj edit <rev>`). */
class JjEditAction : JjLogCommitAction() {

    override fun update(e: AnActionEvent) {
        val target = target(e)
        e.presentation.isVisible = target != null
        // Disabled on `@`: the working copy is already there, so editing it is a no-op.
        e.presentation.isEnabled = target != null &&
            e.project?.let { JjWorkingCopyCache.getInstance(it).commitId != target.hash } ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return

        runInBackground(
            project,
            JujutsuBundle.message("dialog.edit.task"),
            JujutsuBundle.message("dialog.edit.error"),
        ) {
            target.repo.edit(target.hash)
        }
    }
}