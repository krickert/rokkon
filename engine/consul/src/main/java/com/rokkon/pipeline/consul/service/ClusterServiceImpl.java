package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.model.PipelineClusterConfig;
import com.rokkon.pipeline.config.model.PipelineGraphConfig;
import com.rokkon.pipeline.config.model.PipelineModuleMap;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import com.rokkon.pipeline.validation.ValidationResult;
import com.rokkon.pipeline.validation.ValidationResultFactory;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ClusterServiceImpl implements ClusterService {
    private static final Logger LOG = Logger.getLogger(ClusterServiceImpl.class);
    
    @ConfigProperty(name = "pipeline.consul.kv-prefix", defaultValue = "pipeline")
    String kvPrefix;

    @Inject
    ConsulConnectionManager connectionManager;

    @Inject
    ObjectMapper objectMapper;

    private ConsulClient getConsulClient() {
        return connectionManager.getClient().orElseThrow(() -> 
            new WebApplicationException("Consul not connected", Response.Status.SERVICE_UNAVAILABLE)
        );
    }

    public Uni<ValidationResult> createCluster(String clusterName) {
        LOG.infof("Creating cluster: %s", clusterName);

        // Validate cluster name
        if (clusterName == null || clusterName.trim().isEmpty()) {
            return Uni.createFrom().item(
                ValidationResultFactory.failure("Cluster name cannot be empty")
            );
        }

        // Check if cluster already exists
        return clusterExists(clusterName)
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().item(
                        ValidationResultFactory.failure("Cluster '" + clusterName + "' already exists")
                    );
                }

                // Create cluster metadata
                ClusterMetadata metadata = new ClusterMetadata(
                    clusterName,
                    Instant.now(),
                    null,
                    Map.of("status", "active", "version", "1.0")
                );

                // Store metadata first
                return storeClusterMetadata(clusterName, metadata)
                    .flatMap(result -> {
                        if (!result.valid()) {
                            return Uni.createFrom().item(result);
                        }

                        // Create initial PipelineClusterConfig
                        return createInitialClusterConfig(clusterName);
                    });
            });
    }

    public Uni<Optional<ClusterMetadata>> getCluster(String clusterName) {
        String key = buildClusterKey(clusterName) + "/metadata";

        return UniHelper.toUni(getConsulClient().getValue(key))
            .map(keyValue -> {
                if (keyValue == null || keyValue.getValue() == null) {
                    return Optional.<ClusterMetadata>empty();
                }

                try {
                    String json = keyValue.getValue();
                    ClusterMetadata metadata = objectMapper.readValue(json, ClusterMetadata.class);
                    return Optional.of(metadata);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to parse cluster metadata for %s", clusterName);
                    return Optional.<ClusterMetadata>empty();
                }
            })
            .onFailure().recoverWithItem(error -> {
                LOG.debugf("Failed to get cluster metadata for %s: %s", clusterName, error.getMessage());
                return Optional.empty();
            });
    }

    public Uni<Boolean> clusterExists(String clusterName) {
        return getCluster(clusterName).map(Optional::isPresent);
    }

    public Uni<ValidationResult> deleteCluster(String clusterName) {
        String key = buildClusterKey(clusterName);

        return UniHelper.toUni(getConsulClient().deleteValues(key))
            .map(response -> {
                LOG.infof("Deleted cluster: %s", clusterName);
                return ValidationResultFactory.success();
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Failed to delete cluster: %s", clusterName);
                return ValidationResultFactory.failure("Failed to delete cluster: " + error.getMessage());
            });
    }

    public Uni<List<Cluster>> listClusters() {
        String prefix = kvPrefix + "/clusters";
        LOG.debugf("Listing clusters with prefix: %s", prefix);
        
        return UniHelper.toUni(getConsulClient().getKeys(prefix))
            .onItem().transformToUni(keys -> {
                LOG.debugf("Found %d keys under prefix %s", keys != null ? keys.size() : 0, prefix);
                if (keys != null && !keys.isEmpty()) {
                    LOG.debugf("Keys: %s", keys);
                }
                
                if (keys == null || keys.isEmpty()) {
                    return Uni.createFrom().item(new ArrayList<Cluster>());
                }

                // Extract unique cluster names
                Set<String> clusterNames = new java.util.HashSet<>();
                for (String key : keys) {
                    // Remove the prefix to get the relative path
                    if (key.startsWith(prefix + "/")) {
                        String relativePath = key.substring(prefix.length() + 1);
                        String[] parts = relativePath.split("/");
                        // First part after prefix should be the cluster name
                        if (parts.length > 0 && !parts[0].isEmpty()) {
                            clusterNames.add(parts[0]);
                        }
                    }
                }
                LOG.debugf("Extracted cluster names: %s", clusterNames);

                // Get metadata for each cluster
                List<Uni<Optional<Cluster>>> clusterUnis = new ArrayList<>();
                for (String name : clusterNames) {
                    clusterUnis.add(
                        getCluster(name).map(metaOpt -> {
                            if (metaOpt.isPresent()) {
                                ClusterMetadata meta = metaOpt.get();
                                Map<String, String> metadataStrings = new java.util.HashMap<>();
                                meta.metadata().forEach((k, v) -> metadataStrings.put(k, String.valueOf(v)));

                                // Handle null createdAt gracefully
                                String createdAtStr = meta.createdAt() != null ? 
                                    meta.createdAt().toString() : 
                                    Instant.now().toString();

                                return Optional.of(new Cluster(
                                    name,
                                    createdAtStr,
                                    meta
                                ));
                            }
                            return Optional.<Cluster>empty();
                        })
                    );
                }

                return Uni.combine().all().unis(clusterUnis)
                    .with(list -> {
                        List<Cluster> result = new ArrayList<>();
                        for (Object obj : list) {
                            @SuppressWarnings("unchecked")
                            Optional<Cluster> opt = (Optional<Cluster>) obj;
                            opt.ifPresent(result::add);
                        }
                        return result;
                    });
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Failed to list clusters");
                return new ArrayList<>();
            });
    }

    private Uni<ValidationResult> storeClusterMetadata(String clusterName, ClusterMetadata metadata) {
        try {
            String json = objectMapper.writeValueAsString(metadata);
            String key = buildClusterKey(clusterName) + "/metadata";

            return UniHelper.toUni(getConsulClient().putValue(key, json))
                .map(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        LOG.infof("Created cluster: %s", clusterName);
                        return ValidationResultFactory.success();
                    } else {
                        return ValidationResultFactory.failure("Failed to create cluster in Consul");
                    }
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Failed to store cluster metadata");
                    return ValidationResultFactory.failure("Failed to store cluster metadata: " + error.getMessage());
                });
        } catch (Exception e) {
            LOG.error("Failed to serialize cluster metadata", e);
            return Uni.createFrom().item(ValidationResultFactory.failure("Failed to serialize cluster metadata: " + e.getMessage()));
        }
    }

    private String buildClusterKey(String clusterName) {
        return kvPrefix + "/clusters/" + clusterName;
    }

    private Uni<ValidationResult> createInitialClusterConfig(String clusterName) {
        try {
            // Create initial empty PipelineClusterConfig
            PipelineGraphConfig emptyGraph = new PipelineGraphConfig(Map.of());
            PipelineModuleMap emptyModuleMap = new PipelineModuleMap(Map.of());

            PipelineClusterConfig initialConfig = new PipelineClusterConfig(
                clusterName,
                emptyGraph,
                emptyModuleMap,
                null,  // no default pipeline
                Set.of(),  // no allowed Kafka topics yet
                Set.of()   // no allowed gRPC services yet
            );

            String json = objectMapper.writeValueAsString(initialConfig);
            String key = buildClusterKey(clusterName) + "/config";

            return UniHelper.toUni(getConsulClient().putValue(key, json))
                .map(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        LOG.infof("Created initial config for cluster: %s", clusterName);
                        return ValidationResultFactory.success();
                    } else {
                        return ValidationResultFactory.failure("Failed to create cluster config in Consul");
                    }
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Failed to create initial cluster config");
                    return ValidationResultFactory.failure("Failed to create cluster config: " + error.getMessage());
                });
        } catch (Exception e) {
            LOG.error("Failed to serialize initial cluster config", e);
            return Uni.createFrom().item(ValidationResultFactory.failure("Failed to create cluster config: " + e.getMessage()));
        }
    }
}
