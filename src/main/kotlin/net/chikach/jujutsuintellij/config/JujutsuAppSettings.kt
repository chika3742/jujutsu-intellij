package net.chikach.jujutsuintellij.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "JujutsuAppSettings",
    storages = [Storage("jujutsu.xml")]
)
class JujutsuAppSettings : PersistentStateComponent<JujutsuAppSettings.State> {

    class State {
        var executablePath: String = ""
        var defaultLogRevset: String = "::@"
        var commandTimeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS
        var enableGitDualMode: Boolean = false
    }

    private var state = State()

    @Volatile
    private var cachedDetectedPath: String? = null

    override fun getState(): State = state

    override fun loadState(newState: State) {
        XmlSerializerUtil.copyBean(newState, state)
        cachedDetectedPath = null
    }

    var executablePath: String
        get() = state.executablePath
        set(value) {
            state.executablePath = value
            cachedDetectedPath = null
        }

    var defaultLogRevset: String
        get() = state.defaultLogRevset
        set(value) { state.defaultLogRevset = value }

    var commandTimeoutMs: Long
        get() = state.commandTimeoutMs
        set(value) { state.commandTimeoutMs = value.coerceAtLeast(1_000L) }

    var enableGitDualMode: Boolean
        get() = state.enableGitDualMode
        set(value) { state.enableGitDualMode = value }

    /**
     * Returns the absolute path to jj: user-configured value if non-blank,
     * otherwise an auto-detected path (cached for the session). Null if not found.
     */
    fun resolvedExecutablePath(): String? {
        val configured = state.executablePath.trim()
        if (configured.isNotEmpty()) return configured
        cachedDetectedPath?.let { return it }
        val detected = JujutsuExecutableDetector.detect()
        if (detected != null) cachedDetectedPath = detected
        return detected
    }

    fun invalidateDetectedPath() {
        cachedDetectedPath = null
    }

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MS = 30_000L

        @JvmStatic
        fun currentCommandTimeoutMs(): Long =
            ApplicationManager.getApplication()
                ?.getService(JujutsuAppSettings::class.java)
                ?.commandTimeoutMs
                ?: DEFAULT_COMMAND_TIMEOUT_MS

        @JvmStatic
        fun getInstance(): JujutsuAppSettings = service()
    }
}
