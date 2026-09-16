package io.github.unurgunite.crystal.sdk

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CrystalSettings", storages = [Storage("crystal.xml")])
class CrystalSettings : PersistentStateComponent<CrystalSettings.State> {
    data class State(
        var crystalPath: String = "",
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * Returns the configured Crystal path, or auto-detects if not set.
     */
    fun getEffectiveCrystalPath(): String {
        val configured = myState.crystalPath
        if (configured.isNotBlank()) return configured
        return CrystalSdkDetector.detect() ?: "crystal"
    }

    companion object {
        fun getInstance(project: Project): CrystalSettings = project.getService(CrystalSettings::class.java)
    }
}
