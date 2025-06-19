package com.rokkon.pipeline.engine.setup;

import com.rokkon.pipeline.consul.model.Cluster;
import com.rokkon.pipeline.consul.model.ClusterMetadata;
import com.rokkon.pipeline.consul.service.ClusterService;
import com.rokkon.pipeline.engine.service.ModuleRegistrationService;
import com.rokkon.pipeline.engine.model.ModuleRegistrationRequest;
import com.rokkon.pipeline.engine.model.ModuleRegistrationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Production-ready setup helper with fluent builder pattern for setting up Rokkon environments.
 * Provides reusable steps for cluster creation, gRPC service startup, and module registration.
 * 
 * Example usage:
 * <pre>
 * try (var context = RokkonSetupHelper.builder()
 *         .createCluster("my-cluster")
 *         .withHealthCheck()
 *         .startGrpcServer(myService)
 *         .registerModule("my-module")
 *         .build()) {
 *     // Use context...
 * }
 * </pre>
 */
@ApplicationScoped
public class RokkonSetupHelper {
    
    private static final Logger LOG = Logger.getLogger(RokkonSetupHelper.class);
    
    @Inject
    ClusterService clusterService;
    
    @Inject
    ModuleRegistrationService registrationService;
    
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int httpPort;
    
    @ConfigProperty(name = "quarkus.http.host", defaultValue = "localhost")
    String httpHost;
    
    public RokkonSetupHelper() {}
    
    /**
     * Create a new builder for setting up Rokkon components.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a builder with dependency injection support.
     * Use this when running within a CDI container.
     */
    public Builder injectedBuilder() {
        return new Builder()
                .withClusterService(clusterService)
                .withRegistrationService(registrationService)
                .withHttpEndpoint(httpHost, httpPort);
    }
    
    /**
     * Fluent builder for setting up Rokkon environments
     */
    public static class Builder {
        // Cluster configuration
        private String clusterName;
        private final Map<String, String> clusterMetadata = new HashMap<>();
        private ClusterService clusterService;
        
        // gRPC server configuration
        private Server grpcServer;
        private ManagedChannel grpcChannel;
        private int grpcPort = 0; // 0 means random port
        private String grpcHost = "localhost";
        private final List<io.grpc.BindableService> grpcServices = new ArrayList<>();
        private HealthStatusManager healthManager;
        
        // Module registration configuration
        private String moduleName;
        private final Map<String, String> moduleMetadata = new HashMap<>();
        private ModuleRegistrationService registrationService;
        
        // HTTP client configuration for REST calls
        private String httpHost = "localhost";
        private int httpPort = 8080;
        private Client httpClient;
        
        // Setup options
        private boolean validateSetup = true;
        private Duration setupTimeout = Duration.ofSeconds(30);
        private final List<SetupStep> setupSteps = new ArrayList<>();
        
        public Builder withClusterService(ClusterService service) {
            this.clusterService = service;
            return this;
        }
        
        public Builder withRegistrationService(ModuleRegistrationService service) {
            this.registrationService = service;
            return this;
        }
        
        public Builder withHttpEndpoint(String host, int port) {
            this.httpHost = host;
            this.httpPort = port;
            return this;
        }
        
        public Builder withValidation(boolean validate) {
            this.validateSetup = validate;
            return this;
        }
        
        public Builder withTimeout(Duration timeout) {
            this.setupTimeout = timeout;
            return this;
        }
        
        /**
         * Create a cluster with the given name.
         */
        public Builder createCluster(String clusterName) {
            return createCluster(clusterName, Collections.emptyMap());
        }
        
        /**
         * Create a cluster with the given name and metadata.
         */
        public Builder createCluster(String clusterName, Map<String, String> metadata) {
            this.clusterName = clusterName;
            this.clusterMetadata.clear();
            this.clusterMetadata.putAll(metadata);
            
            // Add creation metadata
            this.clusterMetadata.put("created_by", "RokkonSetupHelper");
            this.clusterMetadata.put("created_at", Instant.now().toString());
            
            setupSteps.add(new SetupStep("create-cluster", () -> createClusterStep()));
            LOG.infof("Configured to create cluster: %s", clusterName);
            return this;
        }
        
        /**
         * Add metadata to the cluster being created.
         */
        public Builder withClusterMetadata(String key, String value) {
            this.clusterMetadata.put(key, value);
            return this;
        }
        
        /**
         * Add a gRPC service to be hosted.
         */
        public Builder withGrpcService(io.grpc.BindableService service) {
            this.grpcServices.add(service);
            LOG.debugf("Added gRPC service: %s", service.getClass().getSimpleName());
            return this;
        }
        
        /**
         * Set a specific port for the gRPC server (default is random).
         */
        public Builder withGrpcPort(int port) {
            this.grpcPort = port;
            return this;
        }
        
        /**
         * Enable health check service for the gRPC server.
         */
        public Builder withHealthCheck() {
            this.healthManager = new HealthStatusManager();
            return this;
        }
        
        /**
         * Start a gRPC server with the configured services.
         */
        public Builder startGrpcServer(io.grpc.BindableService... services) {
            for (var service : services) {
                this.grpcServices.add(service);
            }
            
            setupSteps.add(new SetupStep("start-grpc", () -> {
                try {
                    startGrpcServerStep();
                } catch (IOException e) {
                    throw new SetupException("Failed to start gRPC server", e);
                }
            }));
            return this;
        }
        
        /**
         * Configure the module name for registration.
         */
        public Builder withModuleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }
        
        /**
         * Add metadata to the module being registered.
         */
        public Builder withModuleMetadata(String key, String value) {
            this.moduleMetadata.put(key, value);
            return this;
        }
        
        /**
         * Register the gRPC service as a module.
         */
        public Builder registerModule(String moduleName) {
            this.moduleName = moduleName;
            setupSteps.add(new SetupStep("register-module", () -> registerModuleStep()));
            LOG.infof("Configured to register module: %s", moduleName);
            return this;
        }
        
        /**
         * Add a custom setup step.
         */
        public Builder withCustomStep(String name, Runnable step) {
            setupSteps.add(new SetupStep(name, step));
            return this;
        }
        
        /**
         * Build and execute the setup, returning a context with all created resources.
         */
        public SetupContext build() {
            SetupContext context = new SetupContext();
            context.startTime = Instant.now();
            
            try {
                // Initialize HTTP client if needed
                if (httpClient == null) {
                    httpClient = ClientBuilder.newClient();
                }
                
                // Execute all setup steps in order
                for (SetupStep step : setupSteps) {
                    LOG.infof("Executing setup step: %s", step.name);
                    try {
                        step.action.run();
                        context.completedSteps.add(step.name);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to execute step: %s", step.name);
                        context.failedStep = step.name;
                        context.setupError = e;
                        throw new SetupException("Setup failed at step: " + step.name, e);
                    }
                }
                
                // Populate context with results
                context.clusterName = this.clusterName;
                context.grpcServer = this.grpcServer;
                context.grpcChannel = this.grpcChannel;
                context.grpcPort = this.grpcPort;
                context.healthManager = this.healthManager;
                context.moduleName = this.moduleName;
                context.httpClient = this.httpClient;
                context.setupDuration = Duration.between(context.startTime, Instant.now());
                
                // Validate setup if requested
                if (validateSetup) {
                    validateSetupContext(context);
                }
                
                LOG.infof("Setup completed successfully in %s ms", context.setupDuration.toMillis());
                
            } catch (Exception e) {
                LOG.errorf(e, "Setup failed, cleaning up resources");
                context.cleanup();
                throw new SetupException("Failed to build setup context", e);
            }
            
            return context;
        }
        
        private void createClusterStep() {
            LOG.infof("Creating cluster: %s", clusterName);
            
            Cluster cluster = new Cluster(
                clusterName,
                Instant.now().toString(),
                new ClusterMetadata(clusterMetadata)
            );
            
            String url = String.format("http://%s:%d/clusters", httpHost, httpPort);
            
            try (Response response = httpClient
                    .target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(cluster))) {
                
                int status = response.getStatus();
                if (status == 201) {
                    LOG.infof("Cluster '%s' created successfully", clusterName);
                } else if (status == 409) {
                    LOG.infof("Cluster '%s' already exists", clusterName);
                } else {
                    throw new SetupException("Failed to create cluster, status: " + status);
                }
            }
        }
        
        private void startGrpcServerStep() throws IOException {
            ServerBuilder<?> serverBuilder = ServerBuilder.forPort(grpcPort);
            
            // Add all configured services
            for (io.grpc.BindableService service : grpcServices) {
                serverBuilder.addService(service);
                LOG.debugf("Added service: %s", service.getClass().getSimpleName());
            }
            
            // Add health service if configured
            if (healthManager != null) {
                serverBuilder.addService(healthManager.getHealthService());
                healthManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
                LOG.debug("Added health check service");
            }
            
            grpcServer = serverBuilder.build().start();
            grpcPort = grpcServer.getPort(); // Get actual port if random was used
            
            LOG.infof("Started gRPC server on %s:%d", grpcHost, grpcPort);
            
            // Create channel for client connections
            grpcChannel = ManagedChannelBuilder
                    .forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .build();
        }
        
        private void registerModuleStep() {
            if (grpcServer == null) {
                throw new SetupException("Cannot register module without gRPC server");
            }
            if (clusterName == null) {
                throw new SetupException("Cannot register module without cluster");
            }
            
            LOG.infof("Registering module '%s' with cluster '%s'", moduleName, clusterName);
            
            // Add registration metadata
            moduleMetadata.put("registered_by", "RokkonSetupHelper");
            moduleMetadata.put("registered_at", Instant.now().toString());
            
            var registrationRequest = new ModuleRegistrationRequest(
                moduleName,
                grpcHost,
                grpcPort,
                clusterName,
                "PipeStepProcessor",
                moduleMetadata
            );
            
            String url = String.format("http://%s:%d/modules/register", httpHost, httpPort);
            
            try (Response response = httpClient
                    .target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(registrationRequest))) {
                
                if (response.getStatus() == 200) {
                    ModuleRegistrationResponse regResponse = response.readEntity(ModuleRegistrationResponse.class);
                    if (regResponse.success()) {
                        LOG.infof("Module '%s' registered with ID: %s", moduleName, regResponse.moduleId());
                    } else {
                        throw new SetupException("Module registration failed: " + regResponse.message());
                    }
                } else {
                    throw new SetupException("Failed to register module, status: " + response.getStatus());
                }
            }
        }
        
        private void validateSetupContext(SetupContext context) {
            List<String> issues = new ArrayList<>();
            
            if (context.clusterName != null && !verifyClusterExists(context.clusterName)) {
                issues.add("Cluster '" + context.clusterName + "' was not created");
            }
            
            if (context.grpcServer != null && context.grpcServer.isShutdown()) {
                issues.add("gRPC server is not running");
            }
            
            if (context.healthManager != null && !verifyHealthCheck(context)) {
                issues.add("Health check is not responding");
            }
            
            if (!issues.isEmpty()) {
                throw new SetupException("Setup validation failed: " + String.join(", ", issues));
            }
        }
        
        private boolean verifyClusterExists(String clusterName) {
            String url = String.format("http://%s:%d/clusters/%s", httpHost, httpPort, clusterName);
            try (Response response = httpClient.target(url).request().get()) {
                return response.getStatus() == 200;
            } catch (Exception e) {
                LOG.warnf("Failed to verify cluster exists: %s", e.getMessage());
                return false;
            }
        }
        
        private boolean verifyHealthCheck(SetupContext context) {
            // Simple check that health manager is serving
            return context.healthManager != null && 
                   context.healthManager.getHealthService() != null;
        }
        
        private static class SetupStep {
            final String name;
            final Runnable action;
            
            SetupStep(String name, Runnable action) {
                this.name = name;
                this.action = action;
            }
        }
    }
    
    /**
     * Context containing all created resources and setup information
     */
    public static class SetupContext implements AutoCloseable {
        // Resources
        public String clusterName;
        public Server grpcServer;
        public ManagedChannel grpcChannel;
        public int grpcPort;
        public HealthStatusManager healthManager;
        public String moduleId;
        public String moduleName;
        public Client httpClient;
        
        // Setup tracking
        public Instant startTime;
        public Duration setupDuration;
        public List<String> completedSteps = new ArrayList<>();
        public String failedStep;
        public Exception setupError;
        
        /**
         * Get a summary of the setup context.
         */
        public Map<String, Object> getSummary() {
            Map<String, Object> summary = new HashMap<>();
            summary.put("clusterName", clusterName);
            summary.put("grpcPort", grpcPort);
            summary.put("moduleName", moduleName);
            summary.put("setupDuration", setupDuration != null ? setupDuration.toMillis() + "ms" : "N/A");
            summary.put("completedSteps", completedSteps);
            summary.put("failed", failedStep != null);
            if (failedStep != null) {
                summary.put("failedStep", failedStep);
            }
            return summary;
        }
        
        /**
         * Wait for the gRPC server to be ready.
         */
        public CompletableFuture<Void> awaitReady(Duration timeout) {
            return CompletableFuture.runAsync(() -> {
                long deadline = System.currentTimeMillis() + timeout.toMillis();
                while (System.currentTimeMillis() < deadline) {
                    if (grpcServer != null && !grpcServer.isShutdown() && !grpcServer.isTerminated()) {
                        return;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for server", e);
                    }
                }
                throw new RuntimeException("Server not ready within timeout");
            });
        }
        
        /**
         * Cleanup all resources created during setup.
         */
        public void cleanup() {
            List<Exception> cleanupErrors = new ArrayList<>();
            
            // Shutdown gRPC channel
            if (grpcChannel != null && !grpcChannel.isShutdown()) {
                grpcChannel.shutdown();
                try {
                    if (!grpcChannel.awaitTermination(5, TimeUnit.SECONDS)) {
                        grpcChannel.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    grpcChannel.shutdownNow();
                    cleanupErrors.add(e);
                }
            }
            
            // Shutdown gRPC server
            if (grpcServer != null && !grpcServer.isShutdown()) {
                grpcServer.shutdown();
                try {
                    if (!grpcServer.awaitTermination(5, TimeUnit.SECONDS)) {
                        grpcServer.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    grpcServer.shutdownNow();
                    cleanupErrors.add(e);
                }
            }
            
            // Close HTTP client
            if (httpClient != null) {
                try {
                    httpClient.close();
                } catch (Exception e) {
                    cleanupErrors.add(e);
                }
            }
            
            if (!cleanupErrors.isEmpty()) {
                LOG.warnf("Encountered %d errors during cleanup", cleanupErrors.size());
                cleanupErrors.forEach(e -> LOG.debugf(e, "Cleanup error"));
            } else {
                LOG.info("Setup context cleaned up successfully");
            }
        }
        
        @Override
        public void close() {
            cleanup();
        }
    }
    
    /**
     * Exception thrown when setup fails
     */
    public static class SetupException extends RuntimeException {
        public SetupException(String message) {
            super(message);
        }
        
        public SetupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}