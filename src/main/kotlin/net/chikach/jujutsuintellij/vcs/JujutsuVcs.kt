package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.update.UpdateEnvironment
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.vcs.JujutsuVcs.Companion.getInstance

class JujutsuVcs(project: Project) : AbstractVcs(project, VCS_NAME) {

    override fun getDisplayName(): String = JujutsuBundle.message("vcs.name")

    override fun getType(): VcsType = VcsType.distributed

    override fun getChangeProvider(): ChangeProvider = project.service<JjChangeProvider>()

    override fun getDiffProvider(): DiffProvider = project.service<JjDiffProvider>()

    override fun getVcsHistoryProvider(): VcsHistoryProvider = project.service<JjHistoryProvider>()

    override fun getAnnotationProvider(): AnnotationProvider = project.service<JjAnnotationProvider>()

    override fun getCheckinEnvironment(): CheckinEnvironment = project.service<JjCheckinEnvironment>()

    override fun getRollbackEnvironment(): RollbackEnvironment = project.service<JjRollbackEnvironment>()

    override fun getUpdateEnvironment(): UpdateEnvironment = project.service<JjUpdateEnvironment>()

    override fun getMergeProvider(): MergeProvider = project.service<JjMergeProvider>()

    companion object {
        const val VCS_NAME: String = "Jujutsu"
        val KEY: VcsKey = createKey(VCS_NAME)

        @JvmStatic
        fun getInstance(project: Project): JujutsuVcs? =
            ProjectLevelVcsManager.getInstance(project).findVcsByName(VCS_NAME) as? JujutsuVcs

        /**
         * Returns true when at least one directory in the project is mapped to Jujutsu VCS.
         *
         * Uses [ProjectLevelVcsManager.directoryMappings] instead of [getInstance] because the latter
         * may return null before [com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl] finishes
         * initialising its VCS instance list. [directoryMappings] is a simple list read that is
         * safe to call from any thread without a read action.
         */
        fun isActiveIn(project: Project): Boolean =
            ProjectLevelVcsManager.getInstance(project).directoryMappings
                .any { it.vcs == VCS_NAME }
    }
}
