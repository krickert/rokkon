package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.engine.service.ConsulHealthService;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
@Startup
public class EngineLifecycle {
    
    private static final Logger LOG = Logger.getLogger(EngineLifecycle.class);
    
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int consulPort;
    
    @Inject
    ConsulHealthService consulHealthService;
    
    void onStart(@Observes StartupEvent ev) {
        LOG.infof("Starting %s engine", applicationName);
        LOG.infof("Consul connection: %s:%d", consulHost, consulPort);
        
        // Just check Consul connectivity, don't worry about clusters
        checkConsulConnection();
        
        LOG.info("Engine started successfully");
    }
    
    private void checkConsulConnection() {
        try {
            // Simple health check to Consul
            var url = String.format("http://%s:%d/v1/status/leader", consulHost, consulPort);
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .build();
            
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        LOG.info("Successfully connected to Consul");
                        consulHealthService.updateStatus(ConsulHealthService.ConsulStatus.UP, null);
                    } else {
                        LOG.warnf("Consul returned status %d", response.statusCode());
                        consulHealthService.updateStatus(ConsulHealthService.ConsulStatus.DOWN, 
                            "Consul returned status " + response.statusCode());
                    }
                })
                .exceptionally(throwable -> {
                    LOG.warnf("Failed to connect to Consul: %s", throwable.getMessage());
                    consulHealthService.updateStatus(ConsulHealthService.ConsulStatus.DOWN, throwable.getMessage());
                    return null;
                });
        } catch (Exception e) {
            LOG.warnf("Error checking Consul connection: %s", e.getMessage());
            consulHealthService.updateStatus(ConsulHealthService.ConsulStatus.DOWN, e.getMessage());
        }
    }
}