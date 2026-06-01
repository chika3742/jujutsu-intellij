package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/** Creates a new bookmark at `@` via `jj bookmark create <name>`. */
class JjBookmarkCreateAction : JjRepositoryAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = findRepo(e) ?: return

        val name = Messages.showInputDialog(
            project,
            "Bookmark name:",
            "Create Bookmark",
            null,
        )?.trim() ?: return
        if (name.isEmpty()) return

        runInBackground(project, "Creating Bookmark", "Bookmark Creation Failed") {
            repo.createBookmark(name)
        }
    }
}
