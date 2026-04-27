package net.chikach.jujutsuintellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import net.chikach.jujutsuintellij.repo.JjWorkingCopyDescription
import net.chikach.jujutsuintellij.vcs.JujutsuVcs.Companion.isActiveIn
import javax.swing.JComponent

/**
 * Top main-toolbar widget showing the current `@`'s description as button text. The
 * [ComboBoxAction] base class renders a chevron on the right and a popup containing
 * "New Change" and "Describe" when clicked.
 */
class JjRevisionWidget : ComboBoxAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || !isActiveIn(project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val descService = JjWorkingCopyDescription.getInstance(project)
        descService.refresh()
        val desc = descService.description
        val display = desc.ifEmpty { NO_DESC }.let { raw ->
            if (raw.length > MAX_LEN) raw.take(MAX_LEN) + "…" else raw
        }
        e.presentation.setText(display, false)
        e.presentation.icon = AllIcons.Vcs.Branch
        e.presentation.description = "Jujutsu working copy: ${desc.ifEmpty { NO_DESC }}"
        e.presentation.isEnabledAndVisible = true
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val am = ActionManager.getInstance()
        return DefaultActionGroup().apply {
            add(am.getAction("Jujutsu.NewChange"))
            add(am.getAction("Jujutsu.Describe"))
        }
    }

    companion object {
        private const val NO_DESC = "(no description)"
        private const val MAX_LEN = 40
    }
}
