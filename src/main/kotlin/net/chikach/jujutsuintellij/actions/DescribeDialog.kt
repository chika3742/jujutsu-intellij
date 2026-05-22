package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import javax.swing.JComponent

/** Multi-line description editor used by the describe actions. */
class DescribeDialog(project: Project, initialText: String, dialogTitle: String) : DialogWrapper(project) {
    private val textArea = JBTextArea(initialText, 8, 60).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    val message: String get() = textArea.text

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent =
        JBScrollPane(textArea).apply {
            preferredSize = Dimension(600, 200)
        }

    override fun getPreferredFocusedComponent(): JComponent = textArea
}
