package net.chikach.jujutsuintellij.ui.log

import com.intellij.vcs.log.VcsRefType
import java.awt.Color

object JjBookmarkRefType : VcsRefType {
    override fun isBranch(): Boolean = true
    override fun getBackgroundColor(): Color = Color(0xC2, 0xE2, 0xFF)
}
