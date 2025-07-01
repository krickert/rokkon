package com.rokkon.pipeline.engine.api;

import com.rokkon.pipeline.engine.service.ConsulHealthService;
import com.rokkon.pipeline.engine.service.EngineRegistrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;

@Path("/api/consul")
public class ConsulStatusResource {
    
    @Inject
    ConsulHealthService consulHealthService;
    
    @Inject
    EngineRegistrationService engineRegistrationService;
    
    @ConfigProperty(name = "engine.host", defaultValue = "")
    String engineHost;
    
    @ConfigProperty(name = "quarkus.grpc.server.port", defaultValue = "49000")
    int grpcPort;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String consulHost;
    
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConsulStatus() {
        ConsulHealthService.ConsulHealthStatus status = consulHealthService.getStatus();
        
        Map<String, Object> response = Map.of(
            "status", status.getStatus().toString(),
            "lastChecked", status.getLastChecked().toString(),
            "error", status.getError() != null ? status.getError() : ""
        );
        
        return Response.ok(response).build();
    }
    
    @GET
    @Path("/registration")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRegistrationInfo() {
        Map<String, Object> response = new HashMap<>();
        
        // Basic configuration info
        response.put("consulHost", consulHost);
        response.put("configuredEngineHost", engineHost);
        response.put("grpcPort", grpcPort);
        
        // Get actual registration details from Consul
        engineRegistrationService.getRegistrationDetailsFromConsul()
            .onItem().transform(consulData -> {
                response.put("isRegistered", consulData != null);
                
                if (consulData != null) {
                    // Registration details from Consul
                    Map<String, Object> registrationDetails = new HashMap<>();
                    registrationDetails.put("serviceId", consulData.get("ID"));
                    registrationDetails.put("serviceName", consulData.get("Service"));
                    registrationDetails.put("registeredAddress", consulData.get("Address"));
                    registrationDetails.put("registeredPort", consulData.get("Port"));
                    registrationDetails.put("tags", consulData.get("Tags"));
                    registrationDetails.put("meta", consulData.get("Meta"));
                    
                    response.put("registration", registrationDetails);
                    
                    // Health check diagnostics
                    Map<String, Object> healthCheckDiagnostics = new HashMap<>();
                    healthCheckDiagnostics.put("expectedHealthCheckEndpoint", "grpc.health.v1.Health/Check");
                    healthCheckDiagnostics.put("healthCheckType", "gRPC");
                    
                    // Get health check details
                    var healthChecks = engineRegistrationService.getHealthCheckDetails();
                    if (healthChecks != null) {
                        healthCheckDiagnostics.put("healthCheckStatus", healthChecks.get("Status"));
                        healthCheckDiagnostics.put("healthCheckOutput", healthChecks.get("Output"));
                        healthCheckDiagnostics.put("healthCheckNotes", healthChecks.get("Notes"));
                        
                        // Diagnose connectivity based on actual health check results
                        String status = (String) healthChecks.get("Status");
                        if ("critical".equals(status)) {
                            String registeredAddr = (String) consulData.get("Address");
                            healthCheckDiagnostics.put("diagnosis", 
                                "Health check failing. Consul cannot reach engine at " + registeredAddr + ":" + grpcPort);
                            
                            if ("localhost".equals(registeredAddr) || "127.0.0.1".equals(registeredAddr)) {
                                healthCheckDiagnostics.put("suggestion", 
                                    "Engine registered with localhost address. Consul may be running in a container/different network namespace.");
                            }
                        }
                    }
                    
                    response.put("healthCheckDiagnostics", healthCheckDiagnostics);
                } else {
                    response.put("registration", Map.of("error", "Not registered with Consul"));
                }
                
                return response;
            })
            .onFailure().recoverWithItem(error -> {
                response.put("error", "Failed to fetch registration details: " + error.getMessage());
                return response;
            })
            .await().indefinitely();
        
        return Response.ok(response).build();
    }
}