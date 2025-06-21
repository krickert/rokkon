package com.rokkon.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(
    name = "rokkon",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Rokkon CLI for module registration and management",
    subcommands = {
        RegisterCommand.class,
        HealthCheckCommand.class
    }
)
public class RokkonCLI {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;
    
    @CommandLine.Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output"
    )
    boolean verbose;
}