package net.chikach.jujutsuintellij.repo

object JjPathUtil {
    fun normalizeRelativePath(path: String): String = path.replace('\\', '/')

    fun relativize(rootPath: String, absolutePath: String): String? {
        val normalizedRoot = normalizeAbsolutePath(rootPath)
        val normalizedAbsolute = normalizeAbsolutePath(absolutePath)
        if (normalizedAbsolute == normalizedRoot) return ""
        val prefix = "$normalizedRoot/"
        if (!normalizedAbsolute.startsWith(prefix)) return null
        return normalizedAbsolute.substring(prefix.length)
    }

    fun isUnderRoot(rootPath: String, absolutePath: String): Boolean =
        relativize(rootPath, absolutePath) != null

    private fun normalizeAbsolutePath(path: String): String {
        val normalized = path.replace('\\', '/')
        return if (normalized.length > 1) normalized.trimEnd('/') else normalized
    }
}
