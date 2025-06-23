package com.rokkon.pipeline.engine.api;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * REST API for managing Consul connections dynamically.
 */
@Path("/api/v1/consul/connection")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Consul Connection", description = "Manage Consul connection dynamically")
public class ConsulConnectionResource {
    
    private static final Logger LOG = Logger.getLogger(ConsulConnectionResource.class);
    
    @Inject
    ConsulConnectionManager connectionManager;
    
    @GET
    @Operation(summary = "Get current Consul connection configuration")
    @APIResponse(
        responseCode = "200",
        description = "Current connection configuration",
        content = @Content(schema = @Schema(implementation = ConnectionStatus.class))
    )
    public Uni<Response> getConnectionStatus() {
        var config = connectionManager.getConfiguration();
        var hasClient = connectionManager.getClient().isPresent();
        
        ConnectionStatus status = new ConnectionStatus(
            config.enabled() && hasClient,
            config.host(),
            config.port(),
            config.getConnectionString()
        );
        
        return Uni.createFrom().item(Response.ok(status).build());
    }
    
    @POST
    @Operation(summary = "Update Consul connection configuration")
    @APIResponse(
        responseCode = "200",
        description = "Connection update successful",
        content = @Content(schema = @Schema(implementation = ConnectionUpdateResult.class))
    )
    @APIResponse(
        responseCode = "400",
        description = "Invalid connection parameters"
    )
    @RequestBody(
        description = "New connection configuration",
        required = true,
        content = @Content(schema = @Schema(implementation = ConnectionUpdateRequest.class))
    )
    public Uni<Response> updateConnection(ConnectionUpdateRequest request) {
        LOG.infof("Updating Consul connection to %s:%d", request.host(), request.port());
        
        if (request.host() == null || request.host().isBlank()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Host is required"))
                    .build()
            );
        }
        
        if (request.port() <= 0 || request.port() > 65535) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Port must be between 1 and 65535"))
                    .build()
            );
        }
        
        return connectionManager.updateConnection(request.host(), request.port())
            .onItem().transform(result -> {
                ConnectionUpdateResult updateResult = new ConnectionUpdateResult(
                    result.success(),
                    result.message(),
                    result.config().host(),
                    result.config().port(),
                    result.config().getConnectionString()
                );
                
                return Response.ok(updateResult).build();
            });
    }
    
    @DELETE
    @Operation(summary = "Disconnect from Consul")
    @APIResponse(
        responseCode = "200",
        description = "Disconnection successful"
    )
    public Uni<Response> disconnect() {
        LOG.info("Disconnecting from Consul via API request");
        
        return connectionManager.disconnect()
            .onItem().transform(result -> {
                ConnectionUpdateResult updateResult = new ConnectionUpdateResult(
                    result.success(),
                    result.message(),
                    "",
                    0,
                    "Not connected"
                );
                
                return Response.ok(updateResult).build();
            });
    }
    
    /**
     * Request to update connection configuration.
     */
    public record ConnectionUpdateRequest(
        @Schema(description = "Consul host address", required = true, example = "localhost")
        String host,
        
        @Schema(description = "Consul port", required = true, example = "8500")
        int port
    ) {}
    
    /**
     * Current connection status.
     */
    public record ConnectionStatus(
        @Schema(description = "Whether currently connected to Consul")
        boolean connected,
        
        @Schema(description = "Current or last configured host")
        String host,
        
        @Schema(description = "Current or last configured port")
        int port,
        
        @Schema(description = "Human-readable connection string")
        String connectionString
    ) {}
    
    /**
     * Result of connection update attempt.
     */
    public record ConnectionUpdateResult(
        @Schema(description = "Whether the operation was successful")
        boolean success,
        
        @Schema(description = "Detailed message about the operation")
        String message,
        
        @Schema(description = "Host that was attempted")
        String host,
        
        @Schema(description = "Port that was attempted")
        int port,
        
        @Schema(description = "Human-readable connection string")
        String connectionString
    ) {}
}