package net.chikach.jujutsuintellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path

@Service(Service.Level.APP)
class JjVersion {

    data class Version(val major: Int, val minor: Int, val patch: Int, val raw: String) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

        override fun toString(): String = raw

        companion object {
            private val REGEX = Regex("""(\d+)\.(\d+)\.(\d+)""")

            fun parse(output: String): Version? {
                val match = REGEX.find(output) ?: return null
                val (a, b, c) = match.destructured
                return Version(a.toInt(), b.toInt(), c.toInt(), "${a}.${b}.${c}")
            }
        }
    }

    @Volatile
    private var cached: Version? = null

    fun cachedVersion(): Version? = cached

    /**
     * Probes `jj --version`. Blocking; caller must not be on the EDT.
     * Returns null if the executable is unavailable, fails, or output cannot be parsed.
     */
    fun detect(workDir: Path? = null): Version? {
        cached?.let { return it }
        val dir = workDir ?: Path.of(System.getProperty("user.home") ?: ".")
        return try {
            val result = JjCommands.getInstance().version(dir)
            if (!result.isSuccess) {
                LOG.info("jj --version failed (exit=${result.exitCode}): ${result.stderr.trim()}")
                return null
            }
            Version.parse(result.stdout)?.also { cached = it }
        } catch (e: JjCliException) {
            LOG.info("jj --version could not be executed: ${e.message}")
            null
        } catch (e: Exception) {
            LOG.warn("Unexpected error probing jj version", e)
            null
        }
    }

    fun invalidate() {
        cached = null
    }

    fun isSupported(version: Version): Boolean = version >= MINIMUM_SUPPORTED

    companion object {
        private val LOG = logger<JjVersion>()
        val MINIMUM_SUPPORTED = Version(0, 15, 0, "0.15.0")

        @JvmStatic
        fun getInstance(): JjVersion = service()
    }
}
