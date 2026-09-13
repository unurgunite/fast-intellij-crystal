package io.github.unurgunite.crystal.debugger

import com.intellij.xdebugger.breakpoints.XBreakpointProperties

class CrystalBreakpointProperties : XBreakpointProperties<CrystalBreakpointProperties>() {
    override fun getState(): CrystalBreakpointProperties = this
    override fun loadState(state: CrystalBreakpointProperties) {}
}
