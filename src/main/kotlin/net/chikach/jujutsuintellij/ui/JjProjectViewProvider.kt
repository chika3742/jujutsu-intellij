package net.chikach.jujutsuintellij.ui

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode

/**
 * Hides the `.jj` administrative directory from the Project View tree, analogous to how
 * other VCS plugins hide `.git`, `.hg`, and `.svn`. The filter is unconditional — any
 * directory literally named `.jj` is removed — because this name is reserved by Jujutsu.
 */
class JjProjectViewProvider : TreeStructureProvider {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings?,
    ): Collection<AbstractTreeNode<*>> {
        if (children.none { it is PsiDirectoryNode && it.virtualFile?.name == JJ_DIR }) {
            return children
        }
        return children.filter { node ->
            val dir = (node as? PsiDirectoryNode)?.virtualFile
            dir == null || dir.name != JJ_DIR
        }
    }

    companion object {
        private const val JJ_DIR = ".jj"
    }
}
