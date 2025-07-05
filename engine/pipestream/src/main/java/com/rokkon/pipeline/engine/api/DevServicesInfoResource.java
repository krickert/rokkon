package com.rokkon.pipeline.engine.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * Resource to provide DevServices information to the frontend.
 * This includes dynamically assigned ports for services like Grafana.
 */
@Path("/api/v1/devservices/info")
@Produces(MediaType.APPLICATION_JSON)
@RegisterForReflection
public class DevServicesInfoResource {
    
    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String profile;
    
    // These properties are set by the observability DevServices when running
    @ConfigProperty(name = "grafana.endpoint", defaultValue = "")
    Optional<String> grafanaEndpoint;
    
    @ConfigProperty(name = "quarkus.otel.exporter.otlp.endpoint", defaultValue = "")
    Optional<String> otlpEndpoint;
    
    @ConfigProperty(name = "otel-collector.url", defaultValue = "")
    Optional<String> otelCollectorUrl;
    
    @GET
    public Response getDevServicesInfo() {
        Map<String, Object> info = new HashMap<>();
        
        // Only expose in dev mode
        if (!"dev".equals(profile)) {
            info.put("enabled", false);
            info.put("message", "DevServices info only available in dev mode");
            return Response.ok(info).build();
        }
        
        info.put("enabled", true);
        
        // Observability endpoints
        Map<String, String> observability = new HashMap<>();
        
        // Extract Grafana URL if available
        grafanaEndpoint.ifPresent(endpoint -> {
            observability.put("grafana", endpoint);
            // Extract just the port from the endpoint
            if (endpoint.startsWith("http://localhost:")) {
                String port = endpoint.substring("http://localhost:".length());
                observability.put("grafanaPort", port);
            }
        });
        
        // OTLP endpoints
        otlpEndpoint.ifPresent(endpoint -> observability.put("otlp", endpoint));
        otelCollectorUrl.ifPresent(url -> observability.put("otelCollector", url));
        
        // Always available in dev mode
        observability.put("metrics", "/q/metrics");
        observability.put("health", "/q/health");
        
        info.put("observability", observability);
        
        return Response.ok(info).build();
    }
}