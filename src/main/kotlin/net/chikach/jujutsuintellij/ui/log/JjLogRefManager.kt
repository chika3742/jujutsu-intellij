package net.chikach.jujutsuintellij.ui.log

import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
import java.awt.Color
import java.io.DataInput
import java.io.DataOutput

class JjLogRefManager : VcsLogRefManager {

    override fun getBranchLayoutComparator(): Comparator<VcsRef> =
        Comparator.comparing { it.name }

    override fun getLabelsOrderComparator(): Comparator<VcsRef> =
        Comparator.comparing { it.name }

    override fun groupForBranchFilter(refs: Collection<VcsRef>): List<RefGroup> =
        refs.filter { it.type != JjVisibleHeadRefType }.map { singleGroup(it) }

    override fun groupForTable(refs: Collection<VcsRef>, compact: Boolean, showTagNames: Boolean): List<RefGroup> =
        refs.filter { it.type != JjVisibleHeadRefType }.map { singleGroup(it) }

    override fun serialize(out: DataOutput, type: VcsRefType) {
        out.writeByte(
            when (type) {
                JjRemoteBookmarkRefType -> 1
                JjVisibleHeadRefType -> 2
                JjTagRefType -> 3
                else -> 0
            }
        )
    }

    override fun deserialize(input: DataInput): VcsRefType =
        when (input.readByte().toInt()) {
            1 -> JjRemoteBookmarkRefType
            2 -> JjVisibleHeadRefType
            3 -> JjTagRefType
            else -> JjBookmarkRefType
        }

    override fun isFavorite(ref: VcsRef): Boolean = false

    override fun setFavorite(ref: VcsRef, favorite: Boolean) {}

    private fun singleGroup(ref: VcsRef): RefGroup = object : RefGroup {
        override fun isExpanded(): Boolean = false
        override fun getName(): String = ref.name
        override fun getRefs(): MutableList<VcsRef> = mutableListOf(ref)
        override fun getColors(): MutableList<Color> = mutableListOf(ref.type.backgroundColor)
    }
}
