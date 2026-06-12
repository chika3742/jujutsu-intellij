package net.chikach.jujutsuintellij.ui.push

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.icons.AllIcons
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import com.intellij.vcs.log.impl.VcsUserImpl
import net.chikach.jujutsuintellij.JujutsuBundle
import net.chikach.jujutsuintellij.repo.JjRepository
import net.chikach.jujutsuintellij.repo.model.JjCommit
import net.chikach.jujutsuintellij.repo.model.JjCommitRef
import net.chikach.jujutsuintellij.repo.model.JjFileChange
import net.chikach.jujutsuintellij.vcs.JjContentRevision
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/** Tag push scope chosen in the dialog's options row. */
enum class TagPushScope { ALL, ON_PUSHED }

/**
 * One repository's input to [JjPushDialog]: its cached refs and the remotes available to push to.
 * [bookmarkFilter], when non-null, restricts the preview/push to those bookmark names.
 */
data class RepoPushInput(
    val repo: JjRepository,
    val refs: List<JjCommitRef>,
    val remotes: List<String>,
    val bookmarkFilter: Set<String>? = null,
)

/** The user's confirmed push choices for one repository, read after the dialog is accepted. */
data class RepoPushSelection(
    val repo: JjRepository,
    val remote: String,
    val allowNew: Boolean,
    val bookmarkNames: List<String>,
    val changes: List<BookmarkPushChange>,
)

/**
 * Read-only preview of a `jj git push`, with a per-repository remote selector and optional tag push.
 *
 * The bookmark changes are computed (off the EDT) from the cached refs in each [RepoPushInput] via
 * [computeChanges]; results are cached by (repo, remote, allowNew) so switching back to a previously
 * shown remote is instant. The tree shows one node per affected bookmark with its pushed commits
 * beneath; selecting a commit shows its details in the right pane.
 */
class JjPushDialog(
    private val project: Project,
    private val inputs: List<RepoPushInput>,
) : DialogWrapper(project) {

    private val multiRepo = inputs.size > 1

    /** Cache of computed changes, keyed by [cacheKey]. */
    private val changesCache = HashMap<String, List<BookmarkPushChange>>()
    private val loadingKeys = HashSet<String>()

    private val selectedRemotes: MutableMap<String, String> = inputs
        .filter { it.remotes.isNotEmpty() }
        .associate { it.repo.rootPath to defaultRemote(it.remotes) }
        .toMutableMap()

    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = PushTreeCellRenderer()
    }

    /** Right pane bottom: the VCS-Log-style details panel for the selected commit. */
    private val detailsPanel = CommitDetailsPanel()

    /** Right pane top: the changed files of the selected commit (double-click shows the file diff). */
    private val changesBrowser = SimpleAsyncChangesBrowser(project, false, false)

    /** Guards against a slow commit-changes load overwriting the pane after the selection moved on. */
    private var selectionSeq = 0

    private val allowNewCheckBox = JBCheckBox(JujutsuBundle.message("dialog.push.allowNew"))
    private val pushTagsCheckBox = JBCheckBox(JujutsuBundle.message("dialog.push.pushTags"))
    private val tagScopeCombo = ComboBox(
        arrayOf(
            JujutsuBundle.message("dialog.push.tagScope.all"),
            JujutsuBundle.message("dialog.push.tagScope.onPushed"),
        )
    ).apply {
        // ComboBox.getPreferredSize() honors this width; without it the label is clipped in the row.
        setMinimumAndPreferredWidth(JBUI.scale(220))
    }

    @Volatile
    private var disposed = false

    init {
        title = JujutsuBundle.message("dialog.push.title")
        allowNewCheckBox.addActionListener { rebuildTree() }
        pushTagsCheckBox.addActionListener { tagScopeCombo.isEnabled = pushTagsCheckBox.isSelected }
        tagScopeCombo.isEnabled = false
        tree.addTreeSelectionListener { onTreeSelection() }
        init()
        rebuildTree()
    }

    override fun dispose() {
        disposed = true
        changesBrowser.shutdown()
        super.dispose()
    }

    override fun createCenterPanel(): JComponent {
        val left = JPanel(BorderLayout()).apply {
            add(buildRemotesPanel(), BorderLayout.NORTH)
            add(JBScrollPane(tree), BorderLayout.CENTER)
        }
        val detailsScroll = JBScrollPane(
            detailsPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        ).apply { border = JBUI.Borders.empty() }
        val right = OnePixelSplitter(true, 0.6f).apply {
            firstComponent = changesBrowser
            secondComponent = detailsScroll
        }
        val splitter = OnePixelSplitter(false, 0.5f).apply {
            firstComponent = left
            secondComponent = right
        }
        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(820, 520)
            add(splitter, BorderLayout.CENTER)
            add(buildOptionsPanel(), BorderLayout.SOUTH)
        }
    }

    private fun buildRemotesPanel(): JComponent {
        val panel = JPanel()
        panel.layout = javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(4, 4, 8, 4)
        for (input in inputs) {
            if (input.remotes.isEmpty()) continue
            val row = JPanel(BorderLayout(JBUI.scale(8), 0))
            val labelText = if (multiRepo) input.repo.root.name
            else JujutsuBundle.message("dialog.push.remote.label")
            row.add(JBLabel(labelText), BorderLayout.WEST)
            val combo = ComboBox(input.remotes.toTypedArray()).apply {
                selectedItem = selectedRemotes[input.repo.rootPath]
                addActionListener {
                    val remote = selectedItem as? String ?: return@addActionListener
                    selectedRemotes[input.repo.rootPath] = remote
                    rebuildTree()
                }
            }
            row.add(combo, BorderLayout.CENTER)
            panel.add(row)
        }
        return panel
    }

    private fun buildOptionsPanel(): JComponent =
        JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(12), JBUI.scale(4))).apply {
            border = JBUI.Borders.emptyTop(8)
            add(allowNewCheckBox)
            add(pushTagsCheckBox)
            add(tagScopeCombo)
        }

    // ─── Tree building / async loading ────────────────────────────────────────

    private fun cacheKey(repo: JjRepository, remote: String): String =
        "${repo.rootPath}|$remote|${allowNewCheckBox.isSelected}"

    private fun rebuildTree() {
        rootNode.removeAllChildren()
        for (input in inputs) {
            val remote = selectedRemotes[input.repo.rootPath] ?: continue
            val parent = if (multiRepo) {
                DefaultMutableTreeNode(RepoNodeData(input.repo, remote)).also { rootNode.add(it) }
            } else {
                rootNode
            }
            val key = cacheKey(input.repo, remote)
            val changes = changesCache[key]
            if (changes == null) {
                parent.add(DefaultMutableTreeNode(LoadingMarker))
                ensureLoaded(input.repo, remote, key)
            } else {
                for (change in changes) {
                    val changeNode = DefaultMutableTreeNode(change)
                    for (commit in change.aheadCommits) {
                        changeNode.add(DefaultMutableTreeNode(CommitNodeData(input.repo, commit)))
                    }
                    parent.add(changeNode)
                }
            }
        }
        treeModel.reload()
        expandAll()
        updateOkState()
    }

    /**
     * Disables the push button and shows an error when any commit to be pushed has no description —
     * `jj git push` refuses such commits, so block before invoking it.
     */
    private fun updateOkState() {
        val undescribed = displayedChanges()
            .flatMap { it.aheadCommits }
            .filter { it.description.isBlank() }
            .map { shortId(it.commitId) }
            .distinct()
        if (undescribed.isEmpty()) {
            setErrorText(null)
            setOKActionEnabled(true)
        } else {
            setErrorText(JujutsuBundle.message("dialog.push.error.noDescription", undescribed.joinToString(", ")))
            setOKActionEnabled(false)
        }
    }

    /** The changes currently shown across all repos (only those whose preview has finished loading). */
    private fun displayedChanges(): List<BookmarkPushChange> =
        inputs.flatMap { input ->
            val remote = selectedRemotes[input.repo.rootPath] ?: return@flatMap emptyList()
            changesCache[cacheKey(input.repo, remote)].orEmpty()
        }

    private fun ensureLoaded(repo: JjRepository, remote: String, key: String) {
        if (key in loadingKeys) return
        loadingKeys += key
        val input = inputs.first { it.repo.rootPath == repo.rootPath }
        val refs = input.refs
        val filter = input.bookmarkFilter
        val allowNew = allowNewCheckBox.isSelected
        ApplicationManager.getApplication().executeOnPooledThread {
            val computed = runCatching { computeChanges(repo, refs, remote, allowNew) }.getOrDefault(emptyList())
                .let { changes -> if (filter == null) changes else changes.filter { it.name in filter } }
            // ModalityState.any(): this dialog is modal, so a plain invokeLater would defer the update
            // until the dialog closes, leaving the tree stuck on "Loading…".
            ApplicationManager.getApplication().invokeLater({
                if (disposed) return@invokeLater
                loadingKeys -= key
                changesCache[key] = computed
                rebuildTree()
            }, ModalityState.any())
        }
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) {
            tree.expandRow(i)
            i++
        }
    }

    private fun onTreeSelection() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val data = node?.userObject as? CommitNodeData ?: return // keep the last commit shown
        val seq = ++selectionSeq
        val presentation = CommitPresentationUtil.buildPresentation(project, commitMetadata(data), HashSet())
        detailsPanel.setCommit(presentation)
        changesBrowser.setChangesToDisplay(emptyList())
        ApplicationManager.getApplication().executeOnPooledThread {
            val changes = runCatching { commitChanges(data.repo, data.commit) }.getOrDefault(emptyList())
            ApplicationManager.getApplication().invokeLater({
                if (!disposed && seq == selectionSeq) changesBrowser.setChangesToDisplay(changes)
            }, ModalityState.any())
        }
    }

    /** Builds VCS-Log commit metadata for [data]'s commit, feeding the details panel. */
    private fun commitMetadata(data: CommitNodeData): VcsCommitMetadata {
        val commit = data.commit
        @Suppress("DEPRECATION")
        val author = VcsUserImpl(commit.authorName, commit.authorEmail)
        val subject = commit.description.lineSequence().firstOrNull().orEmpty()
        val time = commit.authorTime.time
        return VcsCommitMetadataImpl(
            HashImpl.build(commit.commitId),
            commit.parentIds.map { HashImpl.build(it) },
            time,
            data.repo.root,
            subject,
            author,
            commit.description,
            author,
            time,
        )
    }

    /** Builds the [Change] list for [commit]'s diff against its first parent (`jj diff --summary`). */
    private fun commitChanges(repo: JjRepository, commit: JjCommit): List<Change> {
        val parentId = commit.parentIds.firstOrNull() ?: return emptyList()
        fun before(rel: String) = JjContentRevision(repo, rel, parentId)
        fun after(rel: String) = JjContentRevision(repo, rel, commit.commitId)
        return repo.diffSummary(parentId, commit.commitId).map { fc ->
            when (fc.status) {
                JjFileChange.Status.ADDED -> Change(null, after(fc.path), FileStatus.ADDED)
                JjFileChange.Status.MODIFIED -> Change(before(fc.path), after(fc.path), FileStatus.MODIFIED)
                JjFileChange.Status.DELETED -> Change(before(fc.path), null, FileStatus.DELETED)
                JjFileChange.Status.RENAMED -> Change(before(fc.sourcePath!!), after(fc.path), FileStatus.MODIFIED)
                JjFileChange.Status.COPIED -> Change(before(fc.sourcePath!!), after(fc.path), FileStatus.ADDED)
            }
        }
    }

    // ─── Result accessors ─────────────────────────────────────────────────────

    val selections: List<RepoPushSelection>
        get() = inputs.mapNotNull { input ->
            val remote = selectedRemotes[input.repo.rootPath] ?: return@mapNotNull null
            val changes = changesCache[cacheKey(input.repo, remote)].orEmpty()
            RepoPushSelection(input.repo, remote, allowNewCheckBox.isSelected, pushedBookmarkNames(changes), changes)
        }

    val pushTags: Boolean get() = pushTagsCheckBox.isSelected

    val tagScope: TagPushScope
        get() = if (tagScopeCombo.selectedIndex == 1) TagPushScope.ON_PUSHED else TagPushScope.ALL

    private companion object {
        fun defaultRemote(remotes: List<String>): String =
            remotes.firstOrNull { it == "origin" } ?: remotes.first()
    }
}

/** Marker user object for the transient "Loading…" tree node. */
private object LoadingMarker

/** User object for a repository grouping node (shown only with multiple repositories). */
private data class RepoNodeData(val repo: JjRepository, val remote: String)

/** User object for a pushed-commit node; carries the repo so its diff can be loaded on selection. */
private data class CommitNodeData(val repo: JjRepository, val commit: JjCommit)

private fun shortId(id: String?): String = id?.take(8) ?: "—"

private fun actionLabel(action: BookmarkPushAction): String = JujutsuBundle.message(
    when (action) {
        BookmarkPushAction.ADD -> "push.action.add"
        BookmarkPushAction.MOVE_FORWARD -> "push.action.forward"
        BookmarkPushAction.MOVE_SIDEWAYS -> "push.action.sideways"
        BookmarkPushAction.MOVE_BACKWARD -> "push.action.backward"
        BookmarkPushAction.DELETE -> "push.action.delete"
    }
)

private class PushTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: javax.swing.JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val userObject = (value as? DefaultMutableTreeNode)?.userObject
        when (userObject) {
            is RepoNodeData -> {
                icon = AllIcons.Nodes.Module
                append(userObject.repo.root.name)
                append("  (${userObject.remote})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }

            is BookmarkPushChange -> {
                icon = AllIcons.Vcs.Branch
                append(userObject.name)
                append("  ${shortId(userObject.remoteId)} → ${shortId(userObject.localId)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append("  (${actionLabel(userObject.action)})", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
            }

            is CommitNodeData -> {
                val subject = userObject.commit.description.lineSequence().firstOrNull()?.ifBlank { null }
                append(subject ?: "(no description)")
            }

            LoadingMarker -> append(JujutsuBundle.message("dialog.push.loading"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
        }
    }
}
