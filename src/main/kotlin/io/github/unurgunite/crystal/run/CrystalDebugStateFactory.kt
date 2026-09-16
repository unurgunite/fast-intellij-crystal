package io.github.unurgunite.crystal.run

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment

/**
 * Creates the debug [RunProfileState] for a [CrystalRunConfiguration].
 * Implemented in `debugger/` (`CrystalDebuggerStateFactory`, registered as an
 * application service) so `run/` never imports `debugger/` — the old direct
 * `CrystalDebugRunState(...)` construction was a package cycle.
 */
interface CrystalDebugStateFactory {
    fun create(
        environment: ExecutionEnvironment,
        configuration: CrystalRunConfiguration,
    ): RunProfileState?
}
