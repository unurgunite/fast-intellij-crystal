package io.github.unurgunite.crystal.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import java.io.File

class CrystalRunState(
    environment: ExecutionEnvironment,
    private val configuration: CrystalRunConfiguration,
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val commandLine = buildCommandLine(configuration)
        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    companion object {
        fun buildCommandLine(configuration: CrystalRunConfiguration): GeneralCommandLine =
            GeneralCommandLine().apply {
                exePath = configuration.crystalPath
                addParameter(configuration.command.command)

                if (configuration.filePath.isNotBlank()) {
                    addParameter(configuration.filePath)
                }

                if (configuration.arguments.isNotBlank()) {
                    // For spec/run, arguments after -- are passed to the program
                    if (configuration.command == CrystalCommand.RUN) {
                        addParameter("--")
                    }
                    addParameters(CrystalCommandLine.splitArgs(configuration.arguments))
                }

                workDirectory = File(configuration.workingDirectory)

                // Parse environment variables (KEY=VALUE per line)
                for ((key, value) in CrystalCommandLine.parseEnvVars(configuration.environmentVariables)) {
                    environment.put(key, value)
                }

                withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            }
    }
}
