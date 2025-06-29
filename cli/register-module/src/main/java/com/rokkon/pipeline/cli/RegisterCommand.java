package com.rokkon.pipeline.cli;

import com.rokkon.pipeline.cli.service.ModuleRegistrationService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "register",
    description = "Register a module with the Rokkon engine"
)
@RegisterForReflection
public class RegisterCommand implements Callable<Integer> {
    
    private static final Logger LOG = Logger.getLogger(RegisterCommand.class);
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @CommandLine.Option(
        names = {"--module-host"},
        description = "Module gRPC host (default: ${DEFAULT-VALUE})",
        defaultValue = "${MODULE_HOST:-localhost}"
    )
    String moduleHost;
    
    @CommandLine.Option(
        names = {"--module-port"},
        description = "Module gRPC port (default: ${DEFAULT-VALUE})",
        defaultValue = "${MODULE_PORT:-9090}"
    )
    int modulePort;
    
    @CommandLine.Option(
        names = {"--engine-host"},
        description = "Engine API host (default: ${DEFAULT-VALUE})",
        defaultValue = "${ENGINE_HOST:-localhost}"
    )
    String engineHost;
    
    @CommandLine.Option(
        names = {"--engine-port"},
        description = "Engine API port (default: ${DEFAULT-VALUE})",
        defaultValue = "${ENGINE_PORT:-8081}"
    )
    int enginePort;
    
    @CommandLine.Option(
        names = {"--registration-host"},
        description = "Host address to register the module with (what Consul will use for health checks, default: module host)",
        defaultValue = ""
    )
    String registrationHost;
    
    @CommandLine.Option(
        names = {"--registration-port"},
        description = "Port to register the module with (what Consul will use for health checks, default: module port)",
        defaultValue = "-1"
    )
    int registrationPort;
    
    @CommandLine.Option(
        names = {"--skip-health-check"},
        description = "Skip module health check before registration",
        defaultValue = "false"
    )
    boolean skipHealthCheck;
    
    @Override
    public Integer call() throws Exception {
        try {
            LOG.infof("Starting module registration...");
            LOG.infof("Module endpoint: %s:%d", moduleHost, modulePort);
            LOG.infof("Engine endpoint: %s:%d", engineHost, enginePort);
            
            // Use module host/port for registration if not specified
            String hostForRegistration = registrationHost.isEmpty() ? moduleHost : registrationHost;
            int portForRegistration = registrationPort == -1 ? modulePort : registrationPort;
            
            LOG.infof("Module registration endpoint: %s:%d", hostForRegistration, portForRegistration);
            
            // Perform registration
            boolean success = registrationService.registerModule(
                moduleHost, 
                modulePort,
                engineHost,
                enginePort,
                hostForRegistration,
                portForRegistration,
                !skipHealthCheck
            ).await().indefinitely();
            
            if (success) {
                LOG.info("Module registered successfully!");
                return 0;
            } else {
                LOG.error("Module registration failed!");
                return 1;
            }
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during registration");
            return 2;
        }
    }
}