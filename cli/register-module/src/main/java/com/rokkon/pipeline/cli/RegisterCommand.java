package com.rokkon.pipeline.cli;

import com.rokkon.pipeline.cli.service.ModuleRegistrationService;
import io.quarkus.logging.Log;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import jakarta.inject.Inject;
import java.util.concurrent.Callable;

@Command(
    name = "register",
    description = "Register a module with the engine",
    mixinStandardHelpOptions = true
)
public class RegisterCommand implements Callable<Integer> {
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @Option(
        names = {"--module-host"},
        description = "Module gRPC host (default: ${DEFAULT-VALUE})",
        defaultValue = "${MODULE_HOST:-localhost}"
    )
    String moduleHost;
    
    @Option(
        names = {"--module-port"},
        description = "Module gRPC port (default: ${DEFAULT-VALUE})",
        defaultValue = "${MODULE_PORT:-9090}"
    )
    int modulePort;
    
    @Option(
        names = {"--engine-host"},
        description = "Engine API host (default: ${DEFAULT-VALUE})",
        defaultValue = "${ENGINE_HOST:-localhost}"
    )
    String engineHost;
    
    @Option(
        names = {"--engine-port"},
        description = "Engine API port (default: ${DEFAULT-VALUE})",
        defaultValue = "${ENGINE_PORT:-8081}"
    )
    int enginePort;
    
    @Option(
        names = {"--registration-host"},
        description = "Host address to register the module with (what Consul will use for health checks, default: module host)",
        defaultValue = ""
    )
    String registrationHost;
    
    @Option(
        names = {"--registration-port"},
        description = "Port to register the module with (what Consul will use for health checks, default: module port)",
        defaultValue = "-1"
    )
    int registrationPort;
    
    @Option(
        names = {"--skip-health-check"},
        description = "Skip module health check before registration",
        defaultValue = "false"
    )
    boolean skipHealthCheck;
    
    @Override
    public Integer call() throws Exception {
        try {
            Log.info("Starting module registration...");
            Log.infof("Module endpoint: %s:%d", moduleHost, modulePort);
            Log.infof("Engine endpoint: %s:%d", engineHost, enginePort);
            
            // Use module host/port for registration if not specified
            String hostForRegistration = registrationHost.isEmpty() ? moduleHost : registrationHost;
            int portForRegistration = registrationPort == -1 ? modulePort : registrationPort;
            
            Log.infof("Module registration endpoint: %s:%d", hostForRegistration, portForRegistration);
            
            // Perform registration
            boolean success = registrationService.registerModule(
                moduleHost, 
                modulePort,
                engineHost,
                enginePort,
                hostForRegistration,
                portForRegistration,
                !skipHealthCheck
            );
            
            if (success) {
                Log.info("Module registered successfully!");
                return 0;
            } else {
                Log.error("Module registration failed!");
                return 1;
            }
        } catch (Exception e) {
            Log.error("Unexpected error during registration", e);
            return 2;
        }
    }
}