package com.rokkon.connectors.filesystem;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * REST resource for the filesystem crawler connector.
 * Provides endpoints to check status and trigger crawls.
 */
@jakarta.ws.rs.Path("/api/crawler")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Filesystem Crawler", description = "Operations for the filesystem crawler connector")
public class FilesystemCrawlerResource {

    private static final Logger LOG = Logger.getLogger(FilesystemCrawlerResource.class);

    @Inject
    FilesystemCrawlerConnector crawlerConnector;

    /**
     * Get the status of the crawler.
     * 
     * @return Response containing the current status and configuration of the crawler
     */
    @GET
    @jakarta.ws.rs.Path("/status")
    @Operation(
        summary = "Get crawler status",
        description = "Returns the current status and configuration of the filesystem crawler"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Successful operation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        )
    })
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "running");
        status.put("rootPath", crawlerConnector.rootPath);
        status.put("fileExtensions", crawlerConnector.fileExtensions);
        status.put("maxFileSize", crawlerConnector.maxFileSize);
        status.put("includeHidden", crawlerConnector.includeHidden);
        status.put("maxDepth", crawlerConnector.maxDepth);
        status.put("batchSize", crawlerConnector.batchSize);
        status.put("deleteOrphans", crawlerConnector.deleteOrphans);

        // Check if the root path exists
        Path rootPath = Paths.get(crawlerConnector.rootPath);
        status.put("rootPathExists", Files.exists(rootPath));

        return Response.ok(status).build();
    }

    /**
     * Trigger a crawl of the configured root path.
     * 
     * @return Response indicating whether the crawl was successfully started
     */
    @POST
    @jakarta.ws.rs.Path("/crawl")
    @Operation(
        summary = "Trigger a crawl",
        description = "Starts a crawl of the configured root path"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Crawl started successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        )
    })
    public Response triggerCrawl() {
        LOG.info("Manually triggering crawl");

        try {
            // Start the crawl in a separate thread
            new Thread(() -> {
                try {
                    crawlerConnector.crawl();
                } catch (Exception e) {
                    LOG.error("Error during crawl", e);
                }
            }).start();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "started");
            response.put("message", "Crawl started successfully");

            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Error triggering crawl", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error triggering crawl: " + e.getMessage());

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(response)
                    .build();
        }
    }

    /**
     * Trigger a crawl with a custom root path.
     * 
     * @param rootPath The path to crawl
     * @return Response indicating whether the crawl was successfully started
     */
    @POST
    @jakarta.ws.rs.Path("/crawl/{rootPath}")
    @Operation(
        summary = "Trigger a crawl with custom path",
        description = "Starts a crawl of the specified path"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Crawl started successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        ),
        @APIResponse(
            responseCode = "400",
            description = "Bad request - path does not exist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        )
    })
    public Response triggerCrawlWithPath(
            @Parameter(description = "The filesystem path to crawl", required = true)
            @jakarta.ws.rs.PathParam("rootPath") String rootPath) {
        LOG.info("Manually triggering crawl with custom root path: " + rootPath);

        try {
            // Validate the path
            Path path = Paths.get(rootPath);
            if (!Files.exists(path)) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Root path does not exist: " + rootPath);

                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(response)
                        .build();
            }

            // Temporarily override the root path
            String originalPath = crawlerConnector.rootPath;
            crawlerConnector.rootPath = rootPath;

            // Start the crawl in a separate thread
            new Thread(() -> {
                try {
                    crawlerConnector.crawl();
                } catch (Exception e) {
                    LOG.error("Error during crawl", e);
                } finally {
                    // Restore the original path
                    crawlerConnector.rootPath = originalPath;
                }
            }).start();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "started");
            response.put("message", "Crawl started successfully with root path: " + rootPath);

            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Error triggering crawl", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error triggering crawl: " + e.getMessage());

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(response)
                    .build();
        }
    }
}
