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
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;

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

    @Inject
    GlobalModuleRegistryService globalRegistry;

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

        // Get all services first, then filter by tag
        return UniHelper.toUni(getConsulClient().catalogServices())
            .onItem().transform(services -> {
                Map<String, List<ModuleInfo>> modulesByCluster = new HashMap<>();

                // Filter services that have the rokkon-module tag
                services.getList().stream()
                    .filter(service -> service.getTags().contains("rokkon-module"))
                    .forEach(service -> {
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
                // Get details for each service including ALL instances
                List<Uni<List<ServiceInfo>>> serviceUnis = serviceList.getList().stream()
                    .map(service -> {
                        String serviceName = service.getName();
                        List<String> tags = service.getTags();

                        // Get all instances (healthy and unhealthy) for each service
                        return UniHelper.toUni(getConsulClient().healthServiceNodes(serviceName, false))
                            .map(healthEntries -> {
                                // Return a list of ServiceInfo for each instance
                                return healthEntries.getList().stream()
                                    .map(entry -> {
                                        Service svc = entry.getService();
                                        // Check if this specific instance is healthy
                                        // Look at ALL checks (both node and service health)
                                        boolean healthy = false; // Default to unhealthy/unknown

                                        if (entry.getChecks() != null && !entry.getChecks().isEmpty()) {
                                            // All checks must pass for the instance to be healthy
                                            healthy = entry.getChecks().stream()
                                                .allMatch(check -> {
                                                    // CheckStatus is an enum with PASSING, WARNING, CRITICAL values
                                                    var status = check.getStatus();
                                                    boolean passing = status == io.vertx.ext.consul.CheckStatus.PASSING;
                                                    LOG.debugf("    Check %s (ServiceID: %s): status=%s, passing=%s", 
                                                        check.getId(), 
                                                        check.getServiceId() != null ? check.getServiceId() : "node", 
                                                        status,
                                                        passing);
                                                    return passing;
                                                });

                                            LOG.infof("Service %s at %s:%d - Total checks: %d, All passing: %s", 
                                                serviceName, svc.getAddress(), svc.getPort(), 
                                                entry.getChecks().size(), healthy);
                                        } else {
                                            LOG.warnf("Service %s at %s:%d - No checks found, defaulting to unhealthy", 
                                                serviceName, svc.getAddress(), svc.getPort());
                                        }

                                        return new ServiceInfo(
                                            serviceName, 
                                            healthy, 
                                            svc.getAddress(), 
                                            svc.getPort(), 
                                            svc.getTags()
                                        );
                                    })
                                    .toList();
                            })
                            .onFailure().recoverWithItem(List.of(new ServiceInfo(serviceName, false, "", 0, tags)));
                    })
                    .toList();

                return Uni.combine().all().unis(serviceUnis)
                    .with(list -> {
                        // Flatten the list of lists into a single list
                        @SuppressWarnings("unchecked")
                        List<List<ServiceInfo>> serviceGroups = (List<List<ServiceInfo>>) list;
                        List<ServiceInfo> allServices = serviceGroups.stream()
                            .flatMap(List::stream)
                            .toList();
                        return Response.ok(allServices).build();
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

    @GET
    @Path("/dashboard")
    @Operation(summary = "Get comprehensive service data for dashboard")
    public Uni<Response> getDashboardData() {
        LOG.info("Fetching dashboard data");

        // Get all services and registered modules in parallel
        Uni<List<ServiceInfo>> allServicesUni = getAllServicesData();
        Uni<Set<GlobalModuleRegistryService.ModuleRegistration>> registeredModulesUni = getRegisteredModules();

        return Uni.combine().all().unis(allServicesUni, registeredModulesUni)
            .with((allServices, registeredModules) -> {
                // Create a map of registered modules by name for quick lookup
                Map<String, List<GlobalModuleRegistryService.ModuleRegistration>> registeredByName = 
                    registeredModules.stream()
                        .collect(Collectors.groupingBy(m -> m.moduleName()));

                // Separate base services from module services
                List<BaseServiceInfo> baseServices = new ArrayList<>();
                List<ModuleServiceInfo> moduleServices = new ArrayList<>();

                // Group services by name
                Map<String, List<ServiceInfo>> servicesByName = allServices.stream()
                    .collect(Collectors.groupingBy(ServiceInfo::name));

                servicesByName.forEach((serviceName, instances) -> {
                    if (serviceName.startsWith("module-")) {
                        // This is a module service
                        String moduleName = serviceName.substring(7);
                        List<GlobalModuleRegistryService.ModuleRegistration> registered = 
                            registeredByName.getOrDefault(moduleName, List.of());

                        List<ModuleInstanceInfo> moduleInstances = instances.stream()
                            .map(instance -> {
                                // Find matching registration
                                GlobalModuleRegistryService.ModuleRegistration reg = registered.stream()
                                    .filter(r -> r.host().equals(instance.address()) && r.port() == instance.port())
                                    .findFirst()
                                    .orElse(null);

                                return new ModuleInstanceInfo(
                                    reg != null ? reg.moduleId() : null,
                                    instance.address(),
                                    instance.port(),
                                    instance.healthy(),
                                    reg != null,
                                    reg != null && reg.enabled(),
                                    reg != null ? reg.metadata().get("containerId") : null,
                                    reg != null ? reg.metadata().get("containerName") : null,
                                    reg != null ? reg.metadata() : Map.of()
                                );
                            })
                            .toList();

                        moduleServices.add(new ModuleServiceInfo(
                            moduleName,
                            serviceName,
                            moduleInstances,
                            instances.get(0).tags() // Use tags from first instance
                        ));
                    } else {
                        // This is a base service
                        List<String> grpcServices = extractGrpcServices(serviceName, instances);

                        List<BaseServiceInstanceInfo> baseInstances = instances.stream()
                            .map(instance -> new BaseServiceInstanceInfo(
                                instance.address(),
                                instance.port(),
                                instance.healthy(),
                                extractVersion(instance.tags()),
                                grpcServices
                            ))
                            .toList();

                        baseServices.add(new BaseServiceInfo(
                            serviceName,
                            determineServiceType(serviceName),
                            baseInstances
                        ));
                    }
                });

                // Sort services: base services first, then modules
                baseServices.sort(Comparator.comparing(BaseServiceInfo::name));
                moduleServices.sort(Comparator.comparing(ModuleServiceInfo::moduleName));

                // Calculate statistics
                int totalModules = moduleServices.size();
                int healthyModules = (int) moduleServices.stream()
                    .filter(m -> m.instances().stream().anyMatch(i -> i.healthy() && i.registered()))
                    .count();
                int zombieCount = (int) moduleServices.stream()
                    .flatMap(m -> m.instances().stream())
                    .filter(i -> !i.registered())
                    .count();

                DashboardData dashboard = new DashboardData(
                    baseServices,
                    moduleServices,
                    new ServiceStatistics(
                        baseServices.size(),
                        totalModules,
                        healthyModules,
                        zombieCount
                    )
                );

                return Response.ok(dashboard).build();
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Failed to fetch dashboard data", throwable);
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Failed to fetch dashboard data"))
                    .build();
            });
    }

    private Uni<List<ServiceInfo>> getAllServicesData() {
        return UniHelper.toUni(getConsulClient().catalogServices())
            .onItem().transformToUni(serviceList -> {
                List<Uni<List<ServiceInfo>>> serviceUnis = serviceList.getList().stream()
                    .map(service -> {
                        String serviceName = service.getName();
                        List<String> tags = service.getTags();

                        return UniHelper.toUni(getConsulClient().healthServiceNodes(serviceName, false))
                            .map(healthEntries -> healthEntries.getList().stream()
                                .map(entry -> {
                                    Service svc = entry.getService();
                                    boolean healthy = entry.getChecks() != null && !entry.getChecks().isEmpty() &&
                                        entry.getChecks().stream().allMatch(check -> 
                                            check.getStatus() == io.vertx.ext.consul.CheckStatus.PASSING);

                                    return new ServiceInfo(serviceName, healthy, svc.getAddress(), 
                                        svc.getPort(), svc.getTags());
                                })
                                .toList()
                            )
                            .onFailure().recoverWithItem(List.of());
                    })
                    .toList();

                return Uni.combine().all().unis(serviceUnis)
                    .with(list -> {
                        @SuppressWarnings("unchecked")
                        List<List<ServiceInfo>> serviceGroups = (List<List<ServiceInfo>>) list;
                        return serviceGroups.stream()
                            .flatMap(List::stream)
                            .toList();
                    });
            });
    }

    private Uni<Set<GlobalModuleRegistryService.ModuleRegistration>> getRegisteredModules() {
        // Get registered modules from the injected service
        return globalRegistry.listRegisteredModules();
    }

    private List<String> extractGrpcServices(String serviceName, List<ServiceInfo> instances) {
        // For rokkon-engine, return the known gRPC services
        if ("rokkon-engine".equals(serviceName)) {
            return List.of("ConnectorEngine", "PipeStreamEngine", "ModuleRegistrationService");
        }

        // For other services, check tags for gRPC service info
        return instances.stream()
            .flatMap(i -> i.tags().stream())
            .filter(tag -> tag.startsWith("grpc-service:"))
            .map(tag -> tag.substring("grpc-service:".length()))
            .distinct()
            .toList();
    }

    private String extractVersion(List<String> tags) {
        return tags.stream()
            .filter(tag -> tag.startsWith("version:"))
            .map(tag -> tag.substring("version:".length()))
            .findFirst()
            .orElse("unknown");
    }

    private String determineServiceType(String serviceName) {
        return switch (serviceName) {
            case "consul" -> "infrastructure";
            case "rokkon-engine" -> "orchestrator";
            default -> "service";
        };
    }

    // New record types for dashboard data
    public record DashboardData(
        List<BaseServiceInfo> baseServices,
        List<ModuleServiceInfo> moduleServices,
        ServiceStatistics statistics
    ) {}

    public record BaseServiceInfo(
        String name,
        String type,
        List<BaseServiceInstanceInfo> instances
    ) {}

    public record BaseServiceInstanceInfo(
        String address,
        int port,
        boolean healthy,
        String version,
        List<String> grpcServices
    ) {}

    public record ModuleServiceInfo(
        String moduleName,
        String serviceName,
        List<ModuleInstanceInfo> instances,
        List<String> tags
    ) {}

    public record ModuleInstanceInfo(
        String moduleId,
        String address,
        int port,
        boolean healthy,
        boolean registered,
        boolean enabled,
        String containerId,
        String containerName,
        Map<String, String> metadata
    ) {}

    public record ServiceStatistics(
        int totalBaseServices,
        int totalModules,
        int healthyModules,
        int zombieCount
    ) {}
}
