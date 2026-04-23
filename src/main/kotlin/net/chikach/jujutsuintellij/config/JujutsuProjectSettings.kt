package net.chikach.jujutsuintellij.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "JujutsuProjectSettings",
    storages = [Storage("jujutsu.xml")]
)
class JujutsuProjectSettings : PersistentStateComponent<JujutsuProjectSettings.State> {

    class State {
        /** Optional per-project override. Empty string means "use app-level default". */
        var customLogRevset: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(newState: State) {
        XmlSerializerUtil.copyBean(newState, state)
    }

    var customLogRevset: String
        get() = state.customLogRevset
        set(value) { state.customLogRevset = value }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): JujutsuProjectSettings = project.service()
    }
}
