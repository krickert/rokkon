package com.rokkon.pipeline.engine;

import com.rokkon.pipeline.consul.service.ClusterService;
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
    
    @ConfigProperty(name = "rokkon.cluster.name", defaultValue = "default-cluster")
    String clusterName;
    
    @Inject
    ClusterService clusterService;
    
    void onStart(@Observes StartupEvent ev) {
        LOG.infof("Starting %s engine", applicationName);
        LOG.infof("Consul connection: %s:%d", consulHost, consulPort);
        LOG.infof("Cluster name: %s", clusterName);
        
        // Log if using custom cluster name
        if (!"default-cluster".equals(clusterName)) {
            LOG.infof("Using custom cluster name: '%s'", clusterName);
        }
        
        // Check if cluster exists
        try {
            clusterService.getCluster(clusterName)
                .onItem().invoke(optionalCluster -> {
                    if (optionalCluster.isPresent()) {
                        LOG.infof("Found existing cluster '%s' created at %s", 
                            clusterName, optionalCluster.get().createdAt());
                    } else {
                        LOG.warnf("Cluster '%s' does not exist in Consul", clusterName);
                        LOG.infof("Auto-creating new cluster '%s'", clusterName);
                        createCluster();
                    }
                })
                .await().atMost(Duration.ofSeconds(10));
        } catch (Exception e) {
            LOG.warnf("Could not check/create cluster: %s. Continuing anyway.", e.getMessage());
        }
        
        LOG.info("Engine started successfully");
    }
    
    private void createCluster() {
        ClusterService.ClusterMetadata metadata = new ClusterService.ClusterMetadata(
            clusterName,
            Instant.now(),
            null,  // no default pipeline yet
            java.util.Map.of(
                "status", "active", 
                "version", "1.0",
                "autoCreated", "true",
                "createdBy", applicationName
            )
        );
        
        try {
            String key = "rokkon-clusters/" + clusterName + "/metadata";
            String url = "http://" + consulHost + ":" + consulPort + "/v1/kv/" + key;
            
            // Use a simple JSON format for now
            Instant createdAt = Instant.now();
            String json = String.format(
                "{\"name\":\"%s\",\"createdAt\":\"%s\",\"defaultPipeline\":null,\"metadata\":{\"status\":\"active\",\"version\":\"1.0\",\"autoCreated\":\"true\",\"createdBy\":\"%s\"}}",
                clusterName, createdAt.toString(), applicationName
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
                
            HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
                
            LOG.infof("Successfully auto-created cluster '%s' at %s", clusterName, createdAt);
        } catch (Exception e) {
            LOG.errorf("Failed to create cluster '%s': %s", clusterName, e.getMessage());
        }
    }
}