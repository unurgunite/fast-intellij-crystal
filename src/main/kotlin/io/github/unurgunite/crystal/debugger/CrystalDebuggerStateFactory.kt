package io.github.unurgunite.crystal.debugger

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import io.github.unurgunite.crystal.run.CrystalDebugStateFactory
import io.github.unurgunite.crystal.run.CrystalRunConfiguration

/**
 * `debugger/` implementation of [CrystalDebugStateFactory], registered as an
 * application service in plugin.xml. Keeps the `run` → `debugger` dependency
 * behind an interface owned by `run/`.
 */
class CrystalDebuggerStateFactory : CrystalDebugStateFactory {
    override fun create(
        environment: ExecutionEnvironment,
        configuration: CrystalRunConfiguration,
    ): RunProfileState = CrystalDebugRunState(environment, configuration)
}
