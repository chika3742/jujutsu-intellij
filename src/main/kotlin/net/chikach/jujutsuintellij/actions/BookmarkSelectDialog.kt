package net.chikach.jujutsuintellij.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
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

/** Checkbox picker over a list of bookmark names; all are checked by default. */
class BookmarkMultiSelectDialog(
    project: Project,
    bookmarks: List<String>,
    dialogTitle: String,
) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<String>().apply {
        setItems(bookmarks) { it }
        bookmarks.forEach { setItemSelected(it, true) }
    }

    val selectedBookmarks: List<String>
        get() = (0 until checkBoxList.itemsCount)
            .filter { checkBoxList.isItemSelected(it) }
            .mapNotNull { checkBoxList.getItemAt(it) }

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent =
        JBScrollPane(checkBoxList).apply { preferredSize = Dimension(300, 200) }

    override fun getPreferredFocusedComponent(): JComponent = checkBoxList
}

/**
 * Returns the only bookmark (as a singleton) when [bookmarks] has one; otherwise prompts via
 * [BookmarkMultiSelectDialog]. Returns `null` when the dialog is cancelled or nothing is selected.
 */
fun chooseBookmarks(project: Project, bookmarks: List<String>, dialogTitle: String): List<String>? {
    bookmarks.singleOrNull()?.let { return listOf(it) }
    val dialog = BookmarkMultiSelectDialog(project, bookmarks, dialogTitle)
    if (!dialog.showAndGet()) return null
    return dialog.selectedBookmarks.ifEmpty { null }
}