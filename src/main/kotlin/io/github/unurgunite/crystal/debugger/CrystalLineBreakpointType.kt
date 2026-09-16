package io.github.unurgunite.crystal.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

class CrystalLineBreakpointType :
    XLineBreakpointType<CrystalBreakpointProperties>(
        "crystal-line-breakpoint",
        "Crystal Line Breakpoint",
    ) {
    override fun createBreakpointProperties(
        file: VirtualFile,
        line: Int,
    ): CrystalBreakpointProperties = CrystalBreakpointProperties()

    override fun canPutAt(
        file: VirtualFile,
        line: Int,
        project: Project,
    ): Boolean = file.name.endsWith(".cr")
}
