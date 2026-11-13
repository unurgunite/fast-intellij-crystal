package io.github.unurgunite.crystal.debugger

import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.platform.dap.DapBreakpointsDescription
import com.intellij.platform.dap.DebugAdapterDescriptor
import com.intellij.platform.dap.connection.CommandLineDebugAdapterHandle
import com.intellij.platform.dap.connection.DebugAdapterHandle

class CrystalDebugAdapterDescriptor : DebugAdapterDescriptor<CrystalDebugAdapterId>() {
    override val id: CrystalDebugAdapterId = CrystalDebugAdapterId

    override val breakpointsDescription: DapBreakpointsDescription = createBreakpointsDescription()

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
         * Builds the breakpoint description across the 261/262 and 263 platform
         * API shapes. 263 renamed the constructor (added a nullable
         * `functionBreakpointType` third parameter) and dropped the two-argument
         * one, so a direct call compiles against one release line and throws
         * NoSuchMethodError on the other. The matching constructor is picked at
         * runtime instead; bodyBreakpoints stay null on 263 and are the same as
         * the two-argument call on 261/262.
         */
        fun createBreakpointsDescription(): DapBreakpointsDescription {
            val source = CrystalLineBreakpointType::class.java
            val exception = CrystalExceptionBreakpointType::class.java
            val type = DapBreakpointsDescription::class.java
            // 263: (source, exception, function) with function defaulting to null.
            runCatching { type.getConstructor(Class::class.java, Class::class.java, Class::class.java) }
                .getOrNull()
                ?.let { return it.newInstance(source, exception, null) as DapBreakpointsDescription }
            // 261/262: (source, exception). Reflective too, so the compiled
            // constant pool holds no reference to a constructor that 263 dropped.
            return type
                .getConstructor(Class::class.java, Class::class.java)
                .newInstance(source, exception) as DapBreakpointsDescription
        }

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
