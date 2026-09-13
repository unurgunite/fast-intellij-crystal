package io.github.unurgunite.crystal.debugger

import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.platform.dap.DapBreakpointsDescription
import com.intellij.platform.dap.DebugAdapterDescriptor
import com.intellij.platform.dap.connection.CommandLineDebugAdapterHandle
import com.intellij.platform.dap.connection.DebugAdapterHandle

class CrystalDebugAdapterDescriptor(
    private val project: Project,
) : DebugAdapterDescriptor<CrystalDebugAdapterId>() {
    override val id: CrystalDebugAdapterId = CrystalDebugAdapterId

    override val breakpointsDescription: DapBreakpointsDescription =
        DapBreakpointsDescription(
            CrystalLineBreakpointType::class.java,
            CrystalExceptionBreakpointType::class.java,
        )

    override suspend fun launchDebugAdapter(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult?,
        sessionId: String,
    ): DebugAdapterHandle {
        val lldbDapPath = findLldbDap()
        val commandLine = GeneralCommandLine(lldbDapPath)
        return CommandLineDebugAdapterHandle(commandLine)
    }

    private fun findLldbDap(): String {
        val lldbDap = findLldbDapCandidate()
        return lldbDap ?: if (System.getProperty("os.name")?.lowercase()?.contains("win") == true) "lldb-dap.exe" else "lldb-dap"
    }

    companion object {
        /**
         * First executable lldb-dap/lldb-vscode candidate, or null when none is
         * installed. Extracted for testability; PATH lookup is deliberately NOT
         * attempted (fixed candidates only, matching production behavior).
         */
        fun findLldbDapCandidate(): String? {
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            val candidates =
                if (isWindows) {
                    listOf(
                        "C:\\Program Files\\LLVM\\bin\\lldb-dap.exe",
                        "C:\\Program Files\\LLVM\\bin\\lldb-vscode.exe",
                        "C:\\Program Files (x86)\\LLVM\\bin\\lldb-dap.exe",
                        "C:\\Program Files (x86)\\LLVM\\bin\\lldb-vscode.exe",
                    )
                } else {
                    listOf(
                        "/usr/bin/lldb-dap",
                        "/usr/local/bin/lldb-dap",
                        "/usr/bin/lldb-vscode",
                        "/usr/local/bin/lldb-vscode",
                    )
                }
            return candidates.firstOrNull { java.io.File(it).canExecute() }
        }
    }
}
