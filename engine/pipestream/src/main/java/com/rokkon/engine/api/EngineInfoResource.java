package com.rokkon.engine.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import com.rokkon.pipeline.engine.service.EngineRegistrationService;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;

import java.util.Map;
import java.util.HashMap;
import java.time.Instant;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Resource to provide engine information to the frontend.
 */
@Path("/api/v1/engine/info")
@Produces(MediaType.APPLICATION_JSON)
public class EngineInfoResource {
    
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "Rokkon Engine")
    String applicationName;
    
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "UNKNOWN")
    String applicationVersion;
    
    // Alternative: get version from system property if config property fails
    private String getApplicationVersion() {
        if (!"UNKNOWN".equals(applicationVersion)) {
            return applicationVersion;
        }
        // Try system property as fallback
        String sysProp = System.getProperty("project.version");
        return sysProp != null ? sysProp : "UNKNOWN";
    }
    
    
    @ConfigProperty(name = "pipeline.cluster.name", defaultValue = "default")
    String clusterName;
    
    @ConfigProperty(name = "pipeline.engine.name", defaultValue = "engine-1")
    String engineName;
    
    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String profile;
    
    @Inject
    EngineRegistrationService registrationService;
    
    @Inject
    ConsulConnectionManager consulConnectionManager;
    
    @GET
    public Response getEngineInfo() {
        // Determine engine status based on registration and consul connection
        boolean isRegistered = registrationService.isRegistered();
        boolean hasConsulConnection = consulConnectionManager.getClient().isPresent();
        String status = (isRegistered && hasConsulConnection) ? "online" : "offline";
        
        // Get runtime information
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        
        // Build comprehensive info response
        Map<String, Object> info = new HashMap<>();
        
        // Basic application info
        String version = getApplicationVersion();
        info.put("name", applicationName);
        info.put("version", version);
        info.put("status", status);
        info.put("profile", profile);
        info.put("clusterName", clusterName);
        info.put("engineName", engineName);
        info.put("consulConfigSpace", "config/" + applicationName.toLowerCase().replace(" ", "-"));
        info.put("registered", isRegistered);
        info.put("consulConnected", hasConsulConnection);
        
        // Build info
        Map<String, Object> build = new HashMap<>();
        // Build time is when the application started
        build.put("time", Instant.ofEpochMilli(runtimeMXBean.getStartTime()).toString());
        build.put("artifact", applicationName);
        build.put("version", version);
        build.put("group", "com.rokkon.pipeline");
        build.put("name", applicationName + "-" + applicationVersion);
        info.put("build", build);
        
        // Java info
        Map<String, Object> java = new HashMap<>();
        java.put("version", System.getProperty("java.version"));
        java.put("vendor", System.getProperty("java.vendor"));
        java.put("vm.name", System.getProperty("java.vm.name"));
        java.put("vm.version", System.getProperty("java.vm.version"));
        java.put("runtime.name", runtimeMXBean.getName());
        java.put("runtime.version", System.getProperty("java.runtime.version"));
        info.put("java", java);
        
        // OS info
        Map<String, Object> os = new HashMap<>();
        os.put("name", System.getProperty("os.name"));
        os.put("arch", System.getProperty("os.arch"));
        os.put("version", System.getProperty("os.version"));
        os.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("os", os);
        
        // Runtime stats
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("uptime", runtimeMXBean.getUptime());
        runtime.put("startTime", runtimeMXBean.getStartTime());
        runtime.put("freeMemory", Runtime.getRuntime().freeMemory());
        runtime.put("totalMemory", Runtime.getRuntime().totalMemory());
        runtime.put("maxMemory", Runtime.getRuntime().maxMemory());
        info.put("runtime", runtime);
        
        return Response.ok(info).build();
    }
}