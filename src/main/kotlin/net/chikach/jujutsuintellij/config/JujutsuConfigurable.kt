package net.chikach.jujutsuintellij.config

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.cli.JjVersion

class JujutsuConfigurable : BoundSearchableConfigurable(
    JujutsuBundle.message("settings.title"),
    ID,
    ID,
) {

    private val settings = JujutsuAppSettings.getInstance()
    private var settingsPanel: DialogPanel? = null
    private val testResultLabel = JBLabel().apply { isVisible = false }

    override fun createPanel(): DialogPanel {
        val dialogPanel = panel {
            row(JujutsuBundle.message("settings.executablePath.label")) {
                val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle(JujutsuBundle.message("settings.executablePath.browse.title"))
                textFieldWithBrowseButton(descriptor)
                    .bindText(settings::executablePath)
                    .align(Align.FILL)
                    .resizableColumn()
                button(JujutsuBundle.message("settings.testConnection")) {
                    testConnection()
                }
            }
            row("") {
                cell(testResultLabel)
            }
            row {
                comment(JujutsuBundle.message("settings.executablePath.comment"))
            }
            row(JujutsuBundle.message("settings.defaultLogRevset.label")) {
                textField()
                    .bindText(settings::defaultLogRevset)
                    .align(Align.FILL)
                    .comment(JujutsuBundle.message("settings.defaultLogRevset.comment"))
            }
            row(JujutsuBundle.message("settings.commandTimeout.label")) {
                intTextField(1_000..600_000, 1_000)
                    .bindIntText(
                        { settings.commandTimeoutMs.toInt() },
                        { settings.commandTimeoutMs = it.toLong() },
                    )
            }
            row {
                checkBox(JujutsuBundle.message("settings.enableGitDualMode.label"))
                    .bindSelected(settings::enableGitDualMode)
            }
            row {
                comment(JujutsuBundle.message("settings.enableGitDualMode.comment"))
            }
        }
        settingsPanel = dialogPanel
        return dialogPanel
    }

    override fun disposeUIResources() {
        settingsPanel = null
        super.disposeUIResources()
    }

    private fun testConnection() {
        settingsPanel?.apply()
        settings.invalidateDetectedPath()
        val versionService = JjVersion.getInstance()
        versionService.invalidate()

        with(testResultLabel) {
            isVisible = true
            icon = AnimatedIcon.Default.INSTANCE
            text = JujutsuBundle.message("notification.jj.detecting")
        }

        val modality = ModalityState.stateForComponent(testResultLabel)
        ApplicationManager.getApplication().executeOnPooledThread {
            val version = try {
                versionService.detect()
            } catch (_: Exception) {
                null
            }
            ApplicationManager.getApplication().invokeLater({
                when {
                    version == null -> {
                        testResultLabel.icon = AllIcons.General.Error
                        testResultLabel.text = JujutsuBundle.message("notification.jj.notFound")
                    }
                    !versionService.isSupported(version) -> {
                        testResultLabel.icon = AllIcons.General.Warning
                        testResultLabel.text = JujutsuBundle.message(
                            "notification.jj.tooOld",
                            version.raw,
                            JjVersion.MINIMUM_SUPPORTED.raw,
                        )
                    }
                    else -> {
                        testResultLabel.icon = AllIcons.General.InspectionsOK
                        testResultLabel.text = JujutsuBundle.message("notification.jj.detected", version.raw)
                    }
                }
            }, modality)
        }
    }

    companion object {
        const val ID = "net.chikach.jujutsu.settings"
    }
}
