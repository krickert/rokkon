package com.rokkon.pipeline.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@TopCommand
@Command(
    name = "register-module",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "CLI for registering modules with the engine",
    subcommands = {
        RegisterCommand.class
    }
)
public class RegisterModuleCLI implements Runnable {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;
    
    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output"
    )
    boolean verbose;
    
    @Override
    public void run() {
        spec.commandLine().getOut().println("Module Registration CLI - Use 'register-module --help' for available commands");
    }
}