package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.events.ConsulConnectionEvent;
import io.quarkus.runtime.Startup;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.grpc.BindableService;
import io.quarkus.grpc.GrpcService;

/**
 * Handles engine registration with Consul on startup.
 * Automatically discovers and registers all gRPC services.
 */
@ApplicationScoped
@Startup
public class EngineRegistrationService {
    
    private static final Logger LOG = Logger.getLogger(EngineRegistrationService.class);
    
    @ConfigProperty(name = "consul.host", defaultValue = "")
    String consulHost;
    
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "pipeline-engine")
    String applicationName;
    
    @ConfigProperty(name = "quarkus.grpc.server.port", defaultValue = "49000")
    int grpcPort;
    
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "38090")
    int httpPort;
    
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;
    
    @ConfigProperty(name = "engine.host", defaultValue = "")
    String configuredEngineHost;
    
    @Inject
    Vertx vertx;
    
    @Inject
    ConsulConnectionManager consulConnectionManager;
    
    @Inject
    Instance<BindableService> grpcServices;
    
    private volatile boolean isRegistered = false;
    private volatile String serviceId = null;
    private volatile String registeredAddress = null;
    
    @jakarta.annotation.PostConstruct
    void init() {
        // Just log the status, registration will happen via event
        if (!consulHost.isEmpty()) {
            LOG.info("Consul configured, will register engine when connection is established");
        } else {
            LOG.info("Consul not configured, skipping engine registration");
        }
    }
    
    /**
     * Listen for Consul connection events to handle registration.
     */
    public void onConsulConnection(@Observes ConsulConnectionEvent event) {
        if (event.getType() == ConsulConnectionEvent.Type.CONNECTED && !isRegistered) {
            LOG.info("Consul connection established, registering engine");
            registerEngineWithConsul();
        }
    }
    
    /**
     * Register the engine with Consul as a service.
     */
    private void registerEngineWithConsul() {
        if (isRegistered) {
            LOG.debug("Engine already registered with Consul");
            return;
        }
        
        consulConnectionManager.getMutinyClient().ifPresent(client -> {
            // First check if engine is already registered
            client.catalogServiceNodes(applicationName)
                .onItem().transformToUni(services -> {
                    String hostAddress = getHostAddress();
                    
                    // Check if this instance is already registered
                    for (var service : services.getList()) {
                        if (service.getAddress().equals(hostAddress) && 
                            service.getPort() == grpcPort) {
                            LOG.infof("Engine already registered in Consul with ID: %s", service.getId());
                            isRegistered = true;
                            return io.smallrye.mutiny.Uni.createFrom().voidItem();
                        }
                    }
                    
                    // Not registered, proceed with registration
                    serviceId = applicationName + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
                    
                    // Dynamically discover all gRPC services
                    String providedServices = discoverGrpcServices();
            
            // Create service metadata as Map<String, String>
            java.util.Map<String, String> meta = new java.util.HashMap<>();
            meta.put("service-type", "ENGINE");
            meta.put("provides", providedServices);
            meta.put("grpc-port", String.valueOf(grpcPort));
            meta.put("http-port", String.valueOf(httpPort));
            meta.put("version", version);
            
            // Create health check options using gRPC
            CheckOptions checkOptions = new CheckOptions()
                .setName("Engine gRPC Health Check")
                .setGrpc(hostAddress + ":" + grpcPort)
                .setGrpcTls(false)
                .setInterval("10s")
                .setDeregisterAfter("30s");
            
            // Create service options
            ServiceOptions serviceOptions = new ServiceOptions()
                .setId(serviceId)
                .setName(applicationName)
                .setPort(grpcPort)  // Register on gRPC port
                .setAddress(hostAddress)
                .setTags(java.util.Arrays.asList("pipeline", "engine", "grpc", "version:" + version))
                .setMeta(meta)
                .setCheckOptions(checkOptions);
            
                    // Store the address we're registering with
                    registeredAddress = hostAddress;
                    
                    // Register the service using the Mutiny client
                    return client.registerService(serviceOptions)
                        .onItem().transform(success -> {
                            isRegistered = true;
                            LOG.infof("Successfully registered engine with Consul: %s (ID: %s) providing services: %s", 
                                    applicationName, serviceId, providedServices);
                            return success;
                        })
                        .onFailure().invoke(failure -> {
                            LOG.errorf(failure, "Failed to register engine with Consul");
                        });
                })
                .subscribe().with(
                    success -> LOG.debug("Engine registration check completed"),
                    failure -> LOG.errorf(failure, "Failed to check/register engine")
                );
        });
    }
    
    /**
     * Get the host address for service registration.
     * In a containerized environment, this might need adjustment.
     */
    private String getHostAddress() {
        // Check for configuration property first
        if (configuredEngineHost != null && !configuredEngineHost.isEmpty()) {
            LOG.infof("Using configured engine host: %s", configuredEngineHost);
            return configuredEngineHost;
        }
        
        // Check for explicit override via environment variable
        String host = System.getenv("ENGINE_HOST");
        if (host != null && !host.isEmpty()) {
            return host;
        }
        
        // For local development, use actual hostname for proper health checks
        String profile = System.getProperty("quarkus.profile");
        boolean isDevMode = "dev".equals(profile) || "true".equals(System.getProperty("quarkus.dev"));
        LOG.infof("Current Quarkus profile: %s, Dev mode: %s", profile, isDevMode);
        
        if (isDevMode) {
            // Get the actual hostname instead of localhost
            try {
                String hostname = java.net.InetAddress.getLocalHost().getHostName();
                LOG.infof("Using hostname '%s' for registration in dev mode", hostname);
                return hostname;
            } catch (java.net.UnknownHostException e) {
                LOG.warn("Could not get hostname, falling back to localhost", e);
                return "localhost";
            }
        }
        
        // In containers, use hostname
        host = System.getenv("HOSTNAME");
        if (host == null || host.isEmpty()) {
            try {
                host = java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (java.net.UnknownHostException e) {
                LOG.warn("Could not determine host address, using localhost");
                host = "localhost";
            }
        }
        return host;
    }
    
    /**
     * Dynamically discover all gRPC services registered in the application.
     * This uses CDI to find all beans implementing BindableService and annotated with @GrpcService.
     */
    private String discoverGrpcServices() {
        java.util.List<String> services = new java.util.ArrayList<>();
        
        for (BindableService service : grpcServices) {
            // Get the class of the service
            Class<?> serviceClass = service.getClass();
            
            // Check if it has @GrpcService annotation
            if (serviceClass.isAnnotationPresent(GrpcService.class)) {
                // Extract the service name from the class
                String serviceName = extractServiceName(serviceClass);
                if (serviceName != null && !serviceName.isEmpty()) {
                    services.add(serviceName);
                    LOG.debugf("Discovered gRPC service: %s", serviceName);
                }
            }
        }
        
        // If no services found dynamically, return default set
        if (services.isEmpty()) {
            LOG.warn("No gRPC services discovered dynamically, using default set");
            return "connector,registration,pipe-stream";
        }
        
        String result = String.join(",", services);
        LOG.infof("Discovered %d gRPC services: %s", services.size(), result);
        return result;
    }
    
    /**
     * Extract the service name from a gRPC service implementation class.
     * This looks for common patterns in gRPC service names.
     */
    private String extractServiceName(Class<?> serviceClass) {
        String className = serviceClass.getSimpleName();
        
        // Handle standard patterns like "ConnectorEngineImpl" -> "connector"
        if (className.endsWith("Impl")) {
            className = className.substring(0, className.length() - 4);
        }
        if (className.endsWith("ServiceImpl")) {
            className = className.substring(0, className.length() - 11);
        }
        if (className.endsWith("Service")) {
            className = className.substring(0, className.length() - 7);
        }
        if (className.endsWith("Engine")) {
            className = className.substring(0, className.length() - 6);
        }
        
        // Convert to lowercase with hyphens
        return camelToKebab(className);
    }
    
    /**
     * Convert camelCase to kebab-case.
     */
    private String camelToKebab(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        return input.replaceAll("([a-z])([A-Z]+)", "$1-$2")
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                    .toLowerCase();
    }
    
    // Public getters for registration info
    public boolean isRegistered() {
        return isRegistered;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public String getServiceName() {
        return applicationName;
    }
    
    public String getRegisteredAddress() {
        return registeredAddress;
    }
    
    /**
     * Fetch registration details from Consul.
     */
    public io.smallrye.mutiny.Uni<java.util.Map<String, Object>> getRegistrationDetailsFromConsul() {
        if (serviceId == null || !isRegistered) {
            return io.smallrye.mutiny.Uni.createFrom().nullItem();
        }
        
        return consulConnectionManager.getMutinyClient()
            .map(client -> client.catalogServiceNodes(applicationName)
                .onItem().transform(services -> {
                    // Find our specific instance by serviceId
                    for (var service : services.getList()) {
                        if (serviceId.equals(service.getId())) {
                            java.util.Map<String, Object> details = new java.util.HashMap<>();
                            details.put("ID", service.getId());
                            details.put("Service", service.getName());
                            details.put("Address", service.getAddress());
                            details.put("Port", service.getPort());
                            details.put("Tags", service.getTags());
                            details.put("Meta", service.getMeta());
                            return details;
                        }
                    }
                    return null;
                }))
            .orElse(io.smallrye.mutiny.Uni.createFrom().nullItem());
    }
    
    /**
     * Get health check details for the service.
     */
    public java.util.Map<String, Object> getHealthCheckDetails() {
        // This would need to be implemented to fetch actual health check data
        // For now, return null which the API will handle
        return null;
    }
}