package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Runs `jj new` to open a fresh working-copy commit after the current `@`.
 * Equivalent to creating an empty git commit and then checking it out.
 */
class JjNewChangeAction : JjRepositoryAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        runInBackground(project, "Creating New Change", "jj new Failed") {
            repo.newChange()
        }
    }
}
