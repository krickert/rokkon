package com.rokkon.pipeline.cli;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RegisterCommandTest {

    @Test
    void testDefaultOptions() {
        RegisterCommand cmd = new RegisterCommand();
        CommandLine commandLine = new CommandLine(cmd);

        // Parse with no arguments - should use defaults
        commandLine.parseArgs();

        assertThat(cmd.moduleHost).isEqualTo("localhost");
        assertThat(cmd.modulePort).isEqualTo(9090);
        assertThat(cmd.engineHost).isEqualTo("localhost");
        assertThat(cmd.enginePort).isEqualTo(8081);
        assertThat(cmd.registrationHost).isEmpty();
        assertThat(cmd.registrationPort).isEqualTo(-1);
        assertThat(cmd.skipHealthCheck).isFalse();
    }

    @Test
    void testCustomOptions() {
        RegisterCommand cmd = new RegisterCommand();
        CommandLine commandLine = new CommandLine(cmd);

        // Parse with custom arguments
        commandLine.parseArgs(
            "--module-host=module.example.com",
            "--module-port=9999",
            "--engine-host=engine.example.com",
            "--engine-port=8082",
            "--registration-host=consul.example.com",
            "--registration-port=8500",
            "--skip-health-check"
        );

        assertThat(cmd.moduleHost).isEqualTo("module.example.com");
        assertThat(cmd.modulePort).isEqualTo(9999);
        assertThat(cmd.engineHost).isEqualTo("engine.example.com");
        assertThat(cmd.enginePort).isEqualTo(8082);
        assertThat(cmd.registrationHost).isEqualTo("consul.example.com");
        assertThat(cmd.registrationPort).isEqualTo(8500);
        assertThat(cmd.skipHealthCheck).isTrue();
    }

    @Test
    void testEnvironmentVariableDefaults() {
        // When environment variables are set, they should override defaults
        // This test would need to set system properties to simulate env vars
        // For now, just verify the annotations are correct
        RegisterCommand cmd = new RegisterCommand();
        CommandLine commandLine = new CommandLine(cmd);

        CommandLine.Model.OptionSpec moduleHostOption = commandLine.getCommandSpec()
            .findOption("--module-host");
        assertThat(moduleHostOption.defaultValue()).isEqualTo("localhost");

        CommandLine.Model.OptionSpec modulePortOption = commandLine.getCommandSpec()
            .findOption("--module-port");
        assertThat(modulePortOption.defaultValue()).isEqualTo("9090");
    }
}
