package net.chikach.jujutsuintellij.ui.log

import com.intellij.ui.JBColor
import com.intellij.vcs.log.VcsRefType
import java.awt.Color

object JjTagRefType : VcsRefType {
    override fun isBranch(): Boolean = false
    override fun getBackgroundColor(): JBColor = JBColor(Color(0xF2C55C), Color(0x7F6E2A))
}
