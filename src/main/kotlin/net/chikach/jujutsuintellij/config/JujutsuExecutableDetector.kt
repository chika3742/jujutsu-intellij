package net.chikach.jujutsuintellij.config

import com.intellij.openapi.util.SystemInfo
import java.io.File

object JujutsuExecutableDetector {

    private val exeName: String
        get() = if (SystemInfo.isWindows) "jj.exe" else "jj"

    fun detect(): String? {
        searchPath()?.let { return it }
        return wellKnownCandidates().firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }

    private fun searchPath(): String? {
        val pathVar = System.getenv("PATH") ?: return null
        val name = exeName
        for (dir in pathVar.split(File.pathSeparatorChar)) {
            if (dir.isBlank()) continue
            val candidate = File(dir, name)
            if (candidate.isFile && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }
        return null
    }

    private fun wellKnownCandidates(): List<File> = buildList {
        val name = exeName
        System.getenv("HOME")?.let { home ->
            add(File(home, ".cargo/bin/$name"))
            add(File(home, ".local/bin/$name"))
        }
        when {
            SystemInfo.isMac -> {
                add(File("/opt/homebrew/bin/$name"))
                add(File("/usr/local/bin/$name"))
            }
            SystemInfo.isLinux -> {
                add(File("/usr/local/bin/$name"))
                add(File("/usr/bin/$name"))
            }
            SystemInfo.isWindows -> {
                System.getenv("LOCALAPPDATA")?.let { local ->
                    add(File(local, "Programs/jj/$name"))
                }
            }
        }
    }
}
