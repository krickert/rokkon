package com.rokkon.pipeline.engine;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
@Startup
public class EngineLifecycle {
    
    private static final Logger LOG = Logger.getLogger(EngineLifecycle.class);
    
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;
    
    void onStart(@Observes StartupEvent ev) {
        LOG.infof("Starting %s engine", applicationName);
        LOG.info("Engine started successfully");
        // Consul connection is now handled by ConsulConnectionManager
    }
}