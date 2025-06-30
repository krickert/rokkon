package com.rokkon.pipeline.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that the CLI works correctly without starting any servers.
 */
public class CliNoServerTest {
    
    @Test
    public void testCliHelp() {
        // Create the CLI command
        RegisterModuleCLI cli = new RegisterModuleCLI();
        CommandLine cmd = new CommandLine(cli);
        
        // Capture output
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        
        // Run help command
        int exitCode = cmd.execute("--help");
        
        // Verify it executed successfully
        assertThat(exitCode).isEqualTo(0);
        
        // Verify help output contains expected content
        String output = sw.toString();
        assertThat(output).contains("CLI for registering modules with the engine");
        assertThat(output).contains("register");
    }
    
    @Test
    public void testCliVersion() {
        // Create the CLI command
        RegisterModuleCLI cli = new RegisterModuleCLI();
        CommandLine cmd = new CommandLine(cli);
        
        // Capture output
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        
        // Run version command
        int exitCode = cmd.execute("--version");
        
        // Verify it executed successfully
        assertThat(exitCode).isEqualTo(0);
        
        // Verify version output
        String output = sw.toString();
        assertThat(output).contains("1.0.0");
    }
    
    @Test
    public void testRegisterHelp() {
        // Create the CLI command
        RegisterModuleCLI cli = new RegisterModuleCLI();
        CommandLine cmd = new CommandLine(cli);
        
        // Capture output
        StringWriter sw = new StringWriter();
        StringWriter swErr = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        cmd.setErr(new PrintWriter(swErr));
        
        // Run register help command
        int exitCode = cmd.execute("register", "--help");
        
        // Debug: print output if test fails
        if (exitCode != 0) {
            System.err.println("Exit code: " + exitCode);
            System.err.println("Stdout: " + sw.toString());
            System.err.println("Stderr: " + swErr.toString());
        }
        
        // Verify it executed successfully (help typically returns 0)
        assertThat(exitCode).isEqualTo(0);
        
        // Check both stdout and stderr for help output
        String output = sw.toString() + swErr.toString();
        assertThat(output).contains("Register a module with the engine");
        assertThat(output).contains("--module-host");
        assertThat(output).contains("--module-port");
        assertThat(output).contains("--engine-host");
        assertThat(output).contains("--engine-port");
    }
    
    @Test
    public void testNoArgsShowsHelp() {
        // Create the CLI command
        RegisterModuleCLI cli = new RegisterModuleCLI();
        CommandLine cmd = new CommandLine(cli);
        
        // Capture output
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        
        // Run with no arguments
        int exitCode = cmd.execute();
        
        // Verify it executed successfully
        assertThat(exitCode).isEqualTo(0);
        
        // Verify it shows the basic message
        String output = sw.toString();
        assertThat(output).contains("Module Registration CLI - Use 'register-module --help' for available commands");
    }
}