package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.actions.StandardVcsGroup
import net.chikach.jujutsuintellij.vcs.JujutsuVcs

/**
 * Registers the Jujutsu-specific action submenu that `VcsGroupsWrapper` picks up to populate
 * the "Git" / "Subversion" / ... popup in right-click context menus and the main VCS menu.
 *
 * The same class backs both the main-menu and context-menu groups — the plugin.xml wires each
 * group's children separately.
 */
class JujutsuMenu : StandardVcsGroup() {

    override fun getVcs(project: Project): AbstractVcs? = JujutsuVcs.getInstance(project)

    override fun getVcsName(project: Project): String = JujutsuVcs.VCS_NAME
}
