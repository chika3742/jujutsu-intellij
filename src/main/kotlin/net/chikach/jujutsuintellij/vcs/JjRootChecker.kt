package net.chikach.jujutsuintellij.vcs

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vfs.VirtualFile

class JjRootChecker : VcsRootChecker() {

    override fun getSupportedVcs(): VcsKey = JujutsuVcs.KEY

    override fun isRoot(file: VirtualFile): Boolean {
        val jjDir = file.findChild(JJ_DIR) ?: return false
        return jjDir.isValid && jjDir.isDirectory
    }

    override fun isVcsDir(dirName: String): Boolean {
        return dirName == JJ_DIR
    }

    companion object {
        private const val JJ_DIR = ".jj"
    }
}
