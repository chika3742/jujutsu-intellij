package net.chikach.jujutsuintellij.ui.log

import com.intellij.ui.JBColor
import com.intellij.vcs.log.VcsRefType
import java.awt.Color

object JjBookmarkRefType : VcsRefType {
    override fun isBranch(): Boolean = true
    override fun getBackgroundColor(): JBColor = JBColor(Color(0x58B2FF), Color(0xC2E2FF))
}
