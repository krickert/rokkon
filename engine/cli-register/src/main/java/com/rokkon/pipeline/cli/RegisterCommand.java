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
        names = {"--consul-host"},
        description = "Host where module will be registered in Consul (default: module host)",
        defaultValue = ""
    )
    String consulHost;
    
    @CommandLine.Option(
        names = {"--consul-port"},
        description = "Port where module will be registered in Consul (default: module port)",
        defaultValue = "-1"
    )
    int consulPort;
    
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
            
            // Use module host/port for Consul registration if not specified
            String registrationHost = consulHost.isEmpty() ? moduleHost : consulHost;
            int registrationPort = consulPort == -1 ? modulePort : consulPort;
            
            LOG.infof("Consul registration endpoint: %s:%d", registrationHost, registrationPort);
            
            // Perform registration
            boolean success = registrationService.registerModule(
                moduleHost, 
                modulePort,
                engineHost,
                enginePort,
                registrationHost,
                registrationPort,
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