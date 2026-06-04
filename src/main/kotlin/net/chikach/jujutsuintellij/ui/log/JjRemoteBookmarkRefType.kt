package net.chikach.jujutsuintellij.ui.log

import com.intellij.ui.JBColor
import com.intellij.vcs.log.VcsRefType
import java.awt.Color

/**
 * Ref type for untracked remote bookmarks (`name@remote`). Rendered in a muted color to set it apart
 * from local bookmarks ([JjBookmarkRefType], blue).
 */
object JjRemoteBookmarkRefType : VcsRefType {
    override fun isBranch(): Boolean = true
    override fun getBackgroundColor(): JBColor = JBColor(Color(0xB0B0B0), Color(0x6E6E6E))
}
