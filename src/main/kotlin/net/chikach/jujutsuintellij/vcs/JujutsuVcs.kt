package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import net.chikach.jujutsuintellij.JujutsuBundle

class JujutsuVcs(project: Project) : AbstractVcs(project, VCS_NAME) {

    override fun getDisplayName(): String = JujutsuBundle.message("vcs.name")

    override fun getType(): VcsType = VcsType.distributed

    override fun getChangeProvider(): ChangeProvider = project.service<JjChangeProvider>()

    override fun getDiffProvider(): DiffProvider = project.service<JjDiffProvider>()

    override fun getVcsHistoryProvider(): VcsHistoryProvider = project.service<JjHistoryProvider>()

    override fun getAnnotationProvider(): AnnotationProvider = project.service<JjAnnotationProvider>()

    companion object {
        const val VCS_NAME: String = "Jujutsu"
        val KEY: VcsKey = createKey(VCS_NAME)

        @JvmStatic
        fun getInstance(project: Project): JujutsuVcs? =
            ProjectLevelVcsManager.getInstance(project).findVcsByName(VCS_NAME) as? JujutsuVcs
    }
}
