package net.chikach.jujutsuintellij.ui.statusbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.MultipleTextValuesPresentation
import net.chikach.jujutsuintellij.repo.JjWorkingCopyDescription

/**
 * Status-bar widget that shows the current `@` description and opens a popup with
 * "New Change" and "Describe" actions when clicked.
 *
 * The widget refreshes immediately after any jj describe / new / commit operation via
 * [JjWorkingCopyDescription.addChangeListener].
 */
class JjStatusBarWidget(private val project: Project) : StatusBarWidget, MultipleTextValuesPresentation {

    private var statusBar: StatusBar? = null
    private var removeListener: Runnable? = null

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        removeListener = JjWorkingCopyDescription.getInstance(project).addChangeListener {
            statusBar.updateWidget(ID())
        }
        JjWorkingCopyDescription.getInstance(project).refresh()
    }

    override fun dispose() {
        removeListener?.run()
        removeListener = null
        statusBar = null
    }

    override fun getSelectedValue(): String {
        val desc = JjWorkingCopyDescription.getInstance(project).description
        return desc.ifEmpty { "(no description)" }.let { raw ->
            if (raw.length > MAX_LEN) raw.take(MAX_LEN) + "…" else raw
        }
    }

    override fun getTooltipText(): String = "Jujutsu: current working-copy description"

    override fun getPopup(): JBPopup? {
        val component = statusBar?.component ?: return null
        val group = DefaultActionGroup().apply {
            add(ActionManager.getInstance().getAction("Jujutsu.NewChange"))
            add(ActionManager.getInstance().getAction("Jujutsu.Describe"))
        }
        return JBPopupFactory.getInstance().createActionGroupPopup(
            "Jujutsu",
            group,
            DataManager.getInstance().getDataContext(component),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false,
        )
    }

    companion object {
        const val ID = "jj.working.copy"
        private const val MAX_LEN = 35
    }
}
