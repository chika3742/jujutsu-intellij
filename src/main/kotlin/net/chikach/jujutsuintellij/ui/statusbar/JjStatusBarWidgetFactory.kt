package net.chikach.jujutsuintellij.ui.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

class JjStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = JjStatusBarWidget.ID

    override fun getDisplayName(): String = "Jujutsu"

    override fun isAvailable(project: Project): Boolean = JujutsuVcs.isActiveIn(project)

    override fun createWidget(project: Project): StatusBarWidget = JjStatusBarWidget(project)

    override fun isEnabledByDefault(): Boolean = true
}
