package com.rokkon.connectors.filesystem;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check for the filesystem crawler connector.
 */
@ApplicationScoped
@Liveness
@Readiness
public class FilesystemCrawlerHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(FilesystemCrawlerHealthCheck.class);

    @Inject
    FilesystemCrawlerConnector crawlerConnector;

    @ConfigProperty(name = "filesystem-crawler.root-path")
    String rootPath;

    @Override
    public HealthCheckResponse call() {
        boolean isHealthy = true;

        // Check if the root path exists
        Path root = Paths.get(rootPath);
        boolean rootPathExists = Files.exists(root);

        if (!rootPathExists) {
            LOG.warn("Root path does not exist: " + rootPath);
            isHealthy = false;
        }

        // Check if the root path is readable
        boolean rootPathReadable = Files.isReadable(root);

        if (!rootPathReadable) {
            LOG.warn("Root path is not readable: " + rootPath);
            isHealthy = false;
        }

        // In the latest Quarkus version, we need to use a simpler approach
        if (isHealthy) {
            return HealthCheckResponse.up("filesystem-crawler");
        } else {
            return HealthCheckResponse.down("filesystem-crawler");
        }
    }
}
