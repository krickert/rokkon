package com.rokkon.pipeline.engine.api;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.Service;
import io.vertx.ext.consul.ServiceQueryOptions;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/modules")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Module Discovery", description = "Module discovery operations")
public class ModuleDiscoveryResource {
    
    private static final Logger LOG = Logger.getLogger(ModuleDiscoveryResource.class);
    
    @Inject
    Vertx vertx;
    
    @Inject
    com.rokkon.pipeline.consul.connection.ConsulConnectionManager connectionManager;
    
    private ConsulClient getConsulClient() {
        return connectionManager.getClient().orElseThrow(() -> 
            new WebApplicationException("Consul not connected", Response.Status.SERVICE_UNAVAILABLE)
        );
    }
    
    @GET
    @Path("/by-cluster")
    @Operation(summary = "Get all registered modules grouped by cluster")
    public Uni<Response> getModulesByCluster() {
        LOG.info("Fetching all modules grouped by cluster");
        
        ServiceQueryOptions options = new ServiceQueryOptions()
            .setTag("rokkon-module");
        
        return UniHelper.toUni(getConsulClient().catalogServiceNodesWithOptions("", options))
            .onItem().transform(services -> {
                Map<String, List<ModuleInfo>> modulesByCluster = new HashMap<>();
                
                // Group services by cluster
                services.getList().forEach(service -> {
                    String serviceName = service.getName();
                    List<String> tags = service.getTags();
                    
                    // Extract cluster from tags
                    String cluster = tags.stream()
                        .filter(tag -> tag.startsWith("cluster:"))
                        .map(tag -> tag.substring("cluster:".length()))
                        .findFirst()
                        .orElse("unknown");
                    
                    ModuleInfo module = new ModuleInfo(
                        serviceName,
                        extractModuleType(tags),
                        tags
                    );
                    
                    modulesByCluster.computeIfAbsent(cluster, k -> new ArrayList<>())
                        .add(module);
                });
                
                return Response.ok(modulesByCluster).build();
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Failed to fetch modules from Consul", throwable);
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Failed to connect to Consul"))
                    .build();
            });
    }
    
    @GET
    @Path("/{moduleName}/details")
    @Operation(summary = "Get detailed information about a specific module")
    public Uni<Response> getModuleDetails(@PathParam("moduleName") String moduleName) {
        LOG.infof("Fetching details for module: %s", moduleName);
        
        return UniHelper.toUni(getConsulClient().healthServiceNodes(moduleName, true))
            .map(serviceEntries -> {
                if (serviceEntries.getList().isEmpty()) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Module not found"))
                        .build();
                }
                
                // Transform Vert.x ServiceEntry to our format
                List<ServiceInstance> instances = serviceEntries.getList().stream()
                    .map(entry -> {
                        Service service = entry.getService();
                        return new ServiceInstance(
                            service.getId(),
                            entry.getNode().getName(),
                            service.getAddress(),
                            service.getPort(),
                            service.getMeta() != null ? service.getMeta() : Map.of(),
                            service.getTags()
                        );
                    })
                    .toList();
                
                return Response.ok(instances).build();
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Failed to fetch module details", throwable);
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Failed to connect to Consul"))
                    .build();
            });
    }
    
    @GET
    @Path("/all-services")
    @Operation(summary = "Get all services registered in Consul")
    public Uni<Response> getAllServices() {
        LOG.info("Fetching all services from Consul");
        
        return UniHelper.toUni(getConsulClient().catalogServices())
            .onItem().transformToUni(serviceList -> {
                // Get details for each service
                List<Uni<ServiceInfo>> serviceUnis = serviceList.getList().stream()
                    .map(service -> {
                        String serviceName = service.getName();
                        List<String> tags = service.getTags();
                        
                        // Get health status for each service
                        return UniHelper.toUni(getConsulClient().healthServiceNodes(serviceName, true))
                            .map(healthEntries -> {
                                // Service is healthy if we got any healthy nodes back
                                // (passing=true parameter filters to only healthy nodes)
                                boolean healthy = !healthEntries.getList().isEmpty();
                                
                                String address = "";
                                int port = 0;
                                
                                if (!healthEntries.getList().isEmpty()) {
                                    Service firstService = healthEntries.getList().get(0).getService();
                                    address = firstService.getAddress();
                                    port = firstService.getPort();
                                }
                                
                                return new ServiceInfo(serviceName, healthy, address, port, tags);
                            })
                            .onFailure().recoverWithItem(new ServiceInfo(serviceName, false, "", 0, tags));
                    })
                    .toList();
                
                return Uni.combine().all().unis(serviceUnis)
                    .with(list -> {
                        @SuppressWarnings("unchecked")
                        List<ServiceInfo> services = (List<ServiceInfo>) list;
                        return Response.ok(services).build();
                    });
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Failed to fetch all services from Consul", throwable);
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Failed to connect to Consul"))
                    .build();
            });
    }
    
    private String extractModuleType(List<String> tags) {
        // For now, all are PipeStepProcessor, but could be extended
        return "PipeStepProcessor";
    }
    
    public record ModuleInfo(String name, String type, List<String> tags) {}
    
    public record ServiceInfo(
        String name,
        boolean healthy,
        String address,
        int port,
        List<String> tags
    ) {}
    
    public record ServiceInstance(
        String ID,
        String Node,
        String Address,
        int ServicePort,
        Map<String, String> ServiceMeta,
        List<String> ServiceTags
    ) {}
}