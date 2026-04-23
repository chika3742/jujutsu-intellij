package net.chikach.jujutsuintellij.vcs

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsMappingListener
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.config.JujutsuAppSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When Git and Jujutsu are co-located in the same root, keep the project's VCS mapping pointed at
 * Jujutsu unless the user explicitly enables dual mode.
 */
class JjGitCoexistence : ProjectActivity {

    private val reconciling = AtomicBoolean(false)

    override suspend fun execute(project: Project) {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        vcsManager.runAfterInitialization { reconcileMappings(project) }

        project.messageBus.connect().subscribe(
            ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
            VcsMappingListener { reconcileMappings(project) }
        )
    }

    private fun reconcileMappings(project: Project) {
        if (!reconciling.compareAndSet(false, true)) return

        try {
            if (JujutsuAppSettings.getInstance().enableGitDualMode) return

            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            val currentMappings = vcsManager.directoryMappings
            if (currentMappings.isEmpty()) return

            val suppressedRoots = LinkedHashSet<String>()
            val updatedMappings = deduplicateMappings(
                currentMappings.map { mapping ->
                    if (!shouldPreferJujutsu(project, mapping)) {
                        mapping
                    } else {
                        suppressedRoots += displayPath(project, mapping)
                        VcsDirectoryMapping(mapping.directory, JujutsuVcs.VCS_NAME, mapping.rootSettings)
                    }
                }
            )

            if (updatedMappings == currentMappings) return

            vcsManager.directoryMappings = updatedMappings
            notifySuppressed(project, suppressedRoots)
        } finally {
            reconciling.set(false)
        }
    }

    private fun shouldPreferJujutsu(project: Project, mapping: VcsDirectoryMapping): Boolean {
        if (mapping.vcs != GIT_VCS_NAME) return false
        val path = mappingPath(project, mapping) ?: return false
        return Files.isDirectory(path.resolve(JJ_DIR_NAME))
    }

    private fun mappingPath(project: Project, mapping: VcsDirectoryMapping): Path? {
        val directory = if (mapping.isDefaultMapping) project.basePath else mapping.directory
        return directory?.takeIf { it.isNotBlank() }?.let(Path::of)
    }

    private fun displayPath(project: Project, mapping: VcsDirectoryMapping): String =
        mappingPath(project, mapping)?.toString() ?: project.name

    private fun deduplicateMappings(mappings: List<VcsDirectoryMapping>): List<VcsDirectoryMapping> {
        val byDirectory = LinkedHashMap<String, VcsDirectoryMapping>()
        for (mapping in mappings) {
            val existing = byDirectory[mapping.directory]
            if (existing == null || mapping.vcs == JujutsuVcs.VCS_NAME) {
                byDirectory[mapping.directory] = mapping
            }
        }
        return byDirectory.values.toList()
    }

    private fun notifySuppressed(project: Project, suppressedRoots: Set<String>) {
        if (suppressedRoots.isEmpty()) return
        val properties = PropertiesComponent.getInstance(project)
        if (properties.getBoolean(NOTIFICATION_KEY)) return

        val firstRoot = suppressedRoots.first()
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Jujutsu")
            .createNotification(
                JujutsuBundle.message("notification.gitSuppressed.title"),
                JujutsuBundle.message("notification.gitSuppressed.content", firstRoot),
                NotificationType.INFORMATION
            )
            .notify(project)
        properties.setValue(NOTIFICATION_KEY, true)
    }

    companion object {
        private const val GIT_VCS_NAME = "Git"
        private const val JJ_DIR_NAME = ".jj"
        private const val NOTIFICATION_KEY = "net.chikach.jujutsuintellij.gitSuppressedNoticeShown"
    }
}
