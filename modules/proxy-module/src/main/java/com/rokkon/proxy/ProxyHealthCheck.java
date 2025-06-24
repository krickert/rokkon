package com.rokkon.proxy;

import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import com.rokkon.search.sdk.RegistrationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Health check for the proxy service.
 * Verifies that the proxy can connect to the backend module.
 */
@Readiness
@ApplicationScoped
public class ProxyHealthCheck implements HealthCheck {
    private static final Logger LOG = Logger.getLogger(ProxyHealthCheck.class);
    
    @Inject
    ModuleClientFactory clientFactory;
    
    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("proxy-module-backend-connection");
        
        try {
            MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub client = clientFactory.createClient();
            
            // Create a simple registration request (without test request)
            RegistrationRequest request = RegistrationRequest.newBuilder().build();
            
            // Try to connect to the backend module with a timeout
            boolean connected = client.getServiceRegistration(request)
                    .onItem().transform(response -> true)
                    .onFailure().recoverWithItem(false)
                    .await().atMost(Duration.ofSeconds(5));
            
            if (connected) {
                return responseBuilder.up()
                        .withData("backend_connection", "successful")
                        .build();
            } else {
                return responseBuilder.down()
                        .withData("backend_connection", "failed")
                        .withData("reason", "Could not connect to backend module")
                        .build();
            }
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            return responseBuilder.down()
                    .withData("backend_connection", "error")
                    .withData("reason", e.getMessage())
                    .build();
        }
    }
}