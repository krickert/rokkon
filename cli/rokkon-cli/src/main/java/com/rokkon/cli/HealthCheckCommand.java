package com.rokkon.cli;

import com.rokkon.cli.service.ModuleHealthService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "health",
    description = "Check health of a module"
)
@RegisterForReflection
public class HealthCheckCommand implements Callable<Integer> {
    
    private static final Logger LOG = Logger.getLogger(HealthCheckCommand.class);
    
    @Inject
    ModuleHealthService healthService;
    
    @CommandLine.Option(
        names = {"--host"},
        description = "Module host (default: ${DEFAULT-VALUE})",
        defaultValue = "${MODULE_HOST:-localhost}"
    )
    String host;
    
    @CommandLine.Option(
        names = {"--port"},
        description = "Module port (default: ${DEFAULT-VALUE})",
        defaultValue = "${MODULE_PORT:-9090}"
    )
    int port;
    
    @Override
    public Integer call() throws Exception {
        try {
            LOG.infof("Checking module health at %s:%d", host, port);
            
            boolean healthy = healthService.checkModuleHealth(host, port)
                .await().indefinitely();
            
            if (healthy) {
                LOG.info("Module is healthy!");
                return 0;
            } else {
                LOG.error("Module is not healthy!");
                return 1;
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error checking module health");
            return 2;
        }
    }
}