package net.chikach.jujutsuintellij.ui.log

import com.intellij.ui.JBColor
import com.intellij.vcs.log.VcsRefType
import java.awt.Color

/**
 * Ref type for jj visible heads (`heads(all())`), including the working copy `@`.
 *
 * Analogous to git's `HEAD` ref. These refs exist solely so the platform's [com.intellij.vcs.log.data.VcsLogJoiner]
 * can compute removed/rewritten commits from the ref diff during an incremental refresh: jj rewrites
 * commit ids on edit/abandon/rebase, and without a moving ref the joiner cannot drop the stale nodes.
 *
 * These refs are intentionally **not** rendered as labels — [JjLogRefManager] filters them out in
 * `groupForTable` / `groupForBranchFilter` (mirroring git's `groupedRefs.remove(HEAD)`), while keeping
 * them in the ref set that the joiner consumes. The working copy stays visually distinct via
 * [JjLogStyleHighlighter]'s bold/green styling.
 */
object JjVisibleHeadRefType : VcsRefType {
    override fun isBranch(): Boolean = true
    override fun getBackgroundColor(): JBColor = JBColor(Color(0x1A7F37), Color(0x3FB950))
}
