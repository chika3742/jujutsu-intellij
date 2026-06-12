package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/** Single-selection picker over a list of bookmark names. */
class BookmarkSelectDialog(
    project: Project,
    bookmarks: List<String>,
    dialogTitle: String,
) : DialogWrapper(project) {

    private val list = JBList(bookmarks).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        selectedIndex = 0
    }

    val selectedBookmark: String? get() = list.selectedValue

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent =
        JBScrollPane(list).apply { preferredSize = Dimension(300, 200) }

    override fun getPreferredFocusedComponent(): JComponent = list
}

/** Returns the only bookmark when [bookmarks] has one; otherwise prompts via [BookmarkSelectDialog]. */
fun chooseBookmark(project: Project, bookmarks: List<String>, dialogTitle: String): String? {
    bookmarks.singleOrNull()?.let { return it }
    val dialog = BookmarkSelectDialog(project, bookmarks, dialogTitle)
    return if (dialog.showAndGet()) dialog.selectedBookmark else null
}