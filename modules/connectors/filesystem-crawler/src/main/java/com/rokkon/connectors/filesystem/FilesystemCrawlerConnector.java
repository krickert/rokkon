package com.rokkon.connectors.filesystem;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.rokkon.search.engine.ConnectorEngine;
import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.engine.ConnectorResponse;
import com.rokkon.search.model.BatchInfo;
import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A connector that crawls a filesystem path and sends documents to the Rokkon engine.
 */
@ApplicationScoped
public class FilesystemCrawlerConnector {

    private static final Logger LOG = Logger.getLogger(FilesystemCrawlerConnector.class);

    @ConfigProperty(name = "filesystem-crawler.root-path")
    String rootPath;

    @ConfigProperty(name = "filesystem-crawler.connector-type")
    String connectorType;

    @ConfigProperty(name = "filesystem-crawler.connector-id")
    String connectorId;

    @ConfigProperty(name = "filesystem-crawler.file-extensions")
    String fileExtensions;

    @ConfigProperty(name = "filesystem-crawler.max-file-size")
    long maxFileSize;

    @ConfigProperty(name = "filesystem-crawler.include-hidden")
    boolean includeHidden;

    @ConfigProperty(name = "filesystem-crawler.max-depth")
    int maxDepth;

    @ConfigProperty(name = "filesystem-crawler.batch-size")
    int batchSize;

    @ConfigProperty(name = "filesystem-crawler.delete-orphans")
    boolean deleteOrphans;

    @GrpcClient("connector-engine")
    ConnectorEngine connectorEngine;

    // Store processed file paths for orphan detection
    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();

    /**
     * Start the crawler when the application starts.
     */
    void onStart(@Observes StartupEvent ev) {
        LOG.info("Starting filesystem crawler connector");
        LOG.info("Root path: " + rootPath);
        LOG.info("File extensions: " + fileExtensions);

        // Start the crawling process
        crawl();
    }

    /**
     * Clean up when the application shuts down.
     */
    void onStop(@Observes ShutdownEvent ev) {
        LOG.info("Stopping filesystem crawler connector");
    }

    /**
     * Crawl the filesystem and send documents to the engine.
     */
    public void crawl() {
        LOG.info("Starting crawl of " + rootPath);

        Path root = Paths.get(rootPath);
        if (!Files.exists(root)) {
            LOG.error("Root path does not exist: " + rootPath);
            return;
        }

        // Clear the processed files set before starting a new crawl
        processedFiles.clear();

        // Get the list of file extensions to process
        Set<String> extensions = Arrays.stream(fileExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Create a batch ID for this crawl
        String batchId = UUID.randomUUID().toString();

        try {
            // Find all files that match the criteria
            List<Path> filesToProcess = findFiles(root, extensions);
            int totalFiles = filesToProcess.size();

            LOG.info("Found " + totalFiles + " files to process");

            // Process files in batches
            AtomicInteger processedCount = new AtomicInteger(0);

            for (int i = 0; i < filesToProcess.size(); i += batchSize) {
                int end = Math.min(i + batchSize, filesToProcess.size());
                List<Path> batch = filesToProcess.subList(i, end);

                // Process each file in the batch
                batch.forEach(file -> {
                    try {
                        processFile(file, batchId, processedCount.incrementAndGet(), totalFiles);
                        // Add to processed files set for orphan detection
                        processedFiles.add(file.toString());
                    } catch (Exception e) {
                        LOG.error("Error processing file: " + file, e);
                    }
                });

                LOG.info("Processed " + processedCount.get() + " of " + totalFiles + " files");
            }

            LOG.info("Crawl completed. Processed " + processedCount.get() + " files");

            // Handle orphans if enabled
            if (deleteOrphans) {
                handleOrphans(root, extensions);
            }

        } catch (IOException e) {
            LOG.error("Error crawling filesystem", e);
        }
    }

    /**
     * Find all files that match the criteria.
     */
    private List<Path> findFiles(Path root, Set<String> extensions) throws IOException {
        try (Stream<Path> pathStream = Files.walk(root, maxDepth > 0 ? maxDepth : Integer.MAX_VALUE)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            // Check if the file is hidden
                            if (!includeHidden && Files.isHidden(path)) {
                                return false;
                            }

                            // Check file size
                            if (Files.size(path) > maxFileSize) {
                                LOG.debug("Skipping file (too large): " + path);
                                return false;
                            }

                            // Check file extension
                            String extension = FilenameUtils.getExtension(path.toString()).toLowerCase();
                            return extensions.contains(extension);
                        } catch (IOException e) {
                            LOG.warn("Error checking file: " + path, e);
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Process a single file and send it to the engine.
     */
    private void processFile(Path file, String batchId, int currentItem, int totalItems) throws IOException {
        LOG.debug("Processing file: " + file);

        // Read file attributes
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        // Create a PipeDoc for the file
        PipeDoc pipeDoc = createPipeDoc(file, attrs);

        // Create batch info
        BatchInfo batchInfo = createBatchInfo(batchId, currentItem, totalItems);

        // Create connector request
        ConnectorRequest request = ConnectorRequest.newBuilder()
                .setConnectorType(connectorType)
                .setConnectorId(connectorId)
                .setDocument(pipeDoc)
                .setBatchInfo(batchInfo)
                .addTags("filesystem")
                .addTags("file")
                .build();

        // Send to engine
        try {
            ConnectorResponse response = connectorEngine.processConnectorDoc(request)
                    .await().indefinitely();
            if (response.getAccepted()) {
                LOG.debug("Document accepted by engine: " + file + " (Stream ID: " + response.getStreamId() + ")");
            } else {
                LOG.error("Document rejected by engine: " + file + " - " + response.getMessage());
            }
        } catch (Exception e) {
            LOG.error("Error sending document to engine: " + file, e);
            throw new IOException("Failed to send document to engine", e);
        }
    }

    /**
     * Create a PipeDoc for a file.
     */
    private PipeDoc createPipeDoc(Path file, BasicFileAttributes attrs) throws IOException {
        // Generate a unique ID for the document
        String docId = connectorId + ":" + file.toString();

        // Read the file content
        byte[] content = Files.readAllBytes(file);

        // Determine MIME type
        String mimeType = determineMimeType(file.toString());

        // Create a Blob for the file content
        Blob blob = Blob.newBuilder()
                .setData(ByteString.copyFrom(content))
                .setMimeType(mimeType)
                .setFilename(file.getFileName().toString())
                .putMetadata("path", file.toString())
                .putMetadata("size", String.valueOf(attrs.size()))
                .build();

        // Create timestamps
        Timestamp creationTime = Timestamp.newBuilder()
                .setSeconds(attrs.creationTime().toInstant().getEpochSecond())
                .setNanos(attrs.creationTime().toInstant().getNano())
                .build();

        Timestamp lastModifiedTime = Timestamp.newBuilder()
                .setSeconds(attrs.lastModifiedTime().toInstant().getEpochSecond())
                .setNanos(attrs.lastModifiedTime().toInstant().getNano())
                .build();

        Timestamp processedTime = Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .setNanos(Instant.now().getNano())
                .build();

        // Build the PipeDoc
        return PipeDoc.newBuilder()
                .setId(docId)
                .setSourceUri("file://" + file.toAbsolutePath())
                .setSourceMimeType(mimeType)
                .setTitle(file.getFileName().toString())
                .setDocumentType(getDocumentType(file.toString()))
                .setCreationDate(creationTime)
                .setLastModifiedDate(lastModifiedTime)
                .setProcessedDate(processedTime)
                .setBlob(blob)
                .putMetadata("path", file.toString())
                .putMetadata("absolute_path", file.toAbsolutePath().toString())
                .putMetadata("parent_directory", file.getParent().toString())
                .putMetadata("file_size", String.valueOf(attrs.size()))
                .putMetadata("is_symbolic_link", String.valueOf(attrs.isSymbolicLink()))
                .build();
    }

    /**
     * Create batch info for a crawl.
     */
    private BatchInfo createBatchInfo(String batchId, int currentItem, int totalItems) {
        return BatchInfo.newBuilder()
                .setBatchId(batchId)
                .setCurrentItemNumber(currentItem)
                .setTotalItems(totalItems)
                .setBatchName("Filesystem Crawl: " + rootPath)
                .setSourceReference(rootPath)
                .setStartedAt(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build())
                .build();
    }

    /**
     * Determine the MIME type of a file based on its extension.
     */
    private String determineMimeType(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();

        switch (extension) {
            case "txt":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "xml":
                return "application/xml";
            case "json":
                return "application/json";
            case "md":
                return "text/markdown";
            case "csv":
                return "text/csv";
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "svg":
                return "image/svg+xml";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * Determine the document type based on the file extension.
     */
    private String getDocumentType(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();

        switch (extension) {
            case "txt":
                return "text";
            case "html":
            case "htm":
                return "html";
            case "xml":
                return "xml";
            case "json":
                return "json";
            case "md":
                return "markdown";
            case "csv":
                return "csv";
            case "pdf":
                return "pdf";
            case "doc":
            case "docx":
                return "document";
            case "xls":
            case "xlsx":
                return "spreadsheet";
            case "ppt":
            case "pptx":
                return "presentation";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "svg":
                return "image";
            default:
                return "unknown";
        }
    }

    /**
     * Handle orphaned files (files that were processed in a previous crawl but no longer exist).
     */
    private void handleOrphans(Path root, Set<String> extensions) throws IOException {
        LOG.info("Checking for orphaned files");

        // Get the current list of files
        List<Path> currentFiles = findFiles(root, extensions);
        Set<String> currentFilePaths = currentFiles.stream()
                .map(Path::toString)
                .collect(Collectors.toSet());

        // Find files that were processed before but no longer exist
        Set<String> orphanedFiles = new HashSet<>(processedFiles);
        orphanedFiles.removeAll(currentFilePaths);

        LOG.info("Found " + orphanedFiles.size() + " orphaned files");

        // Process each orphaned file
        for (String orphanedFile : orphanedFiles) {
            LOG.debug("Processing orphaned file: " + orphanedFile);

            // Create a PipeDoc for the orphaned file
            String docId = connectorId + ":" + orphanedFile;

            PipeDoc pipeDoc = PipeDoc.newBuilder()
                    .setId(docId)
                    .setSourceUri("file://" + orphanedFile)
                    .putMetadata("path", orphanedFile)
                    .putMetadata("orphaned", "true")
                    .build();

            // Create connector request with DELETE action
            ConnectorRequest request = ConnectorRequest.newBuilder()
                    .setConnectorType(connectorType)
                    .setConnectorId(connectorId)
                    .setDocument(pipeDoc)
                    .addTags("filesystem")
                    .addTags("file")
                    .addTags("orphaned")
                    .build();

            // Send to engine
            try {
                ConnectorResponse response = connectorEngine.processConnectorDoc(request)
                        .await().indefinitely();
                if (response.getAccepted()) {
                    LOG.debug("Orphaned document processed by engine: " + orphanedFile + " (Stream ID: " + response.getStreamId() + ")");
                } else {
                    LOG.error("Orphaned document rejected by engine: " + orphanedFile + " - " + response.getMessage());
                }
            } catch (Exception e) {
                LOG.error("Error sending orphaned document to engine: " + orphanedFile, e);
            }
        }
    }
}
