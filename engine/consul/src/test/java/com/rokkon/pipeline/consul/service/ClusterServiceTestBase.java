package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.validation.ValidationResult;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ClusterServiceTestBase {
    private static final Logger LOG = Logger.getLogger(ClusterServiceTestBase.class);

    protected abstract ClusterService getClusterService();

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testCreateCluster() {
        String clusterName = "base-test-cluster";

        LOG.infof("Testing cluster creation: %s", clusterName);

        ValidationResult result = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        LOG.infof("Create cluster result: valid=%s, errors=%s", 
                  result.valid(), result.errors());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testGetCluster() {
        String clusterName = "base-get-test-cluster";

        // First create a cluster
        ValidationResult createResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(createResult.valid()).isTrue();

        // Now get the cluster
        Optional<ClusterMetadata> cluster = getClusterService().getCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(cluster).isPresent();
        assertThat(cluster.get().name()).isEqualTo(clusterName);
        assertThat(cluster.get().metadata()).containsEntry("status", "active");
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testClusterExists() {
        String clusterName = "base-exists-test-cluster";

        // Check non-existent cluster
        Boolean exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isFalse();

        // Create cluster
        ValidationResult createResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(createResult.valid()).isTrue();

        // Check existing cluster
        exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isTrue();
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testCreateDuplicateCluster() {
        String clusterName = "base-duplicate-test-cluster";

        // Create first cluster
        ValidationResult firstResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(firstResult.valid()).isTrue();

        // Try to create duplicate
        ValidationResult duplicateResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(duplicateResult.valid()).isFalse();
        assertThat(duplicateResult.errors())
            .contains("Cluster '" + clusterName + "' already exists");
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testDeleteCluster() {
        String clusterName = "base-delete-test-cluster";

        // Create cluster
        ValidationResult createResult = getClusterService().createCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(createResult.valid()).isTrue();

        // Verify it exists
        Boolean exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isTrue();

        // Delete cluster
        ValidationResult deleteResult = getClusterService().deleteCluster(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(deleteResult.valid()).isTrue();

        // Verify it's gone
        exists = getClusterService().clusterExists(clusterName)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(exists).isFalse();
    }

    @Test
    @Disabled("Test may fail due to Consul connectivity issues")
    void testEmptyClusterName() {
        // Test with empty string
        ValidationResult result = getClusterService().createCluster("")
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Cluster name cannot be empty");

        // Test with null
        result = getClusterService().createCluster(null)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(Duration.ofSeconds(5))
            .getItem();

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Cluster name cannot be empty");
    }
}
