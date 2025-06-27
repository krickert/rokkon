package com.rokkon.engine.api;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.validation.DefaultValidationResult;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/api/v1/clusters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cluster Management", description = "Operations for managing clusters in Consul")
public class ClusterResource {
    private static final Logger LOG = Logger.getLogger(ClusterResource.class);
    
    @Inject
    ClusterService clusterService;
    
    @GET
    @Operation(summary = "List all clusters")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "List of clusters returned successfully"),
        @APIResponse(responseCode = "500", description = "Failed to retrieve clusters from Consul")
    })
    public Uni<Response> listClusters() {
        LOG.info("REST API: Listing all clusters");
        
        return clusterService.listClusters()
            .map(clusters -> Response.ok(clusters).build())
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to list clusters");
                ValidationResult error = DefaultValidationResult.failure(
                    "Failed to retrieve clusters: " + throwable.getMessage()
                );
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(error)
                    .build();
            });
    }
    
    @POST
    @Path("/{clusterName}")
    @Operation(summary = "Create a new cluster")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Cluster created successfully"),
        @APIResponse(responseCode = "400", description = "Invalid cluster name or cluster already exists")
    })
    public Uni<Response> createCluster(@PathParam("clusterName") String clusterName) {
        LOG.infof("REST API: Creating cluster %s", clusterName);
        
        return clusterService.createCluster(clusterName)
            .map(result -> {
                if (result.valid()) {
                    return Response.status(Response.Status.CREATED)
                        .entity(result)
                        .build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(result)
                        .build();
                }
            });
    }
    
    @GET
    @Path("/{clusterName}")
    @Operation(summary = "Get cluster metadata")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Cluster found"),
        @APIResponse(responseCode = "404", description = "Cluster not found")
    })
    public Uni<Response> getCluster(@PathParam("clusterName") String clusterName) {
        LOG.infof("REST API: Getting cluster %s", clusterName);
        
        return clusterService.getCluster(clusterName)
            .map(cluster -> {
                if (cluster.isPresent()) {
                    return Response.ok(cluster.get()).build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(DefaultValidationResult.failure("Cluster '" + clusterName + "' not found"))
                        .build();
                }
            });
    }
    
    @DELETE
    @Path("/{clusterName}")
    @Operation(summary = "Delete a cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Cluster deleted successfully"),
        @APIResponse(responseCode = "404", description = "Cluster not found")
    })
    public Uni<Response> deleteCluster(@PathParam("clusterName") String clusterName) {
        LOG.infof("REST API: Deleting cluster %s", clusterName);
        
        return clusterService.deleteCluster(clusterName)
            .map(result -> {
                if (result.valid()) {
                    return Response.ok(result).build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(result)
                        .build();
                }
            });
    }
}