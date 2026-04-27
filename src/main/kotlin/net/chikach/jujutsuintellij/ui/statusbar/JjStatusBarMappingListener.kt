package net.chikach.jujutsuintellij.ui.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager

/**
 * Listens for VCS directory-mapping changes and asks [StatusBarWidgetsManager] to re-evaluate
 * the Jujutsu status-bar widget's availability.  This ensures the widget appears as soon as
 * Jujutsu is mapped to a project directory (e.g. after [JjGitCoexistence] reconciles mappings).
 */
class JjStatusBarMappingListener(private val project: Project) : VcsMappingListener {

    @Suppress("UnstableApiUsage")
    override fun directoryMappingChanged() {
        project.getServiceIfCreated(StatusBarWidgetsManager::class.java)
            ?.updateWidget(JjStatusBarWidgetFactory::class.java)
    }
}
