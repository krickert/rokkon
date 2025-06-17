package com.rokkon.test.generation;

import com.rokkon.test.config.TestDataGenerationConfig;
import com.rokkon.test.protobuf.ProtobufUtils;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * Main test data generation coordinator.
 * Handles generating, saving, and managing protobuf test data files.
 */
public class TestDataGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(TestDataGenerator.class);

    // Base path for test data in test-utilities resources
    private static final String TEST_DATA_BASE_PATH = "src/main/resources/test-data";

    /**
     * Generates all test data if regeneration is enabled.
     * This includes tika requests, tika responses, chunker data, embedder data, etc.
     */
    public static void generateAllTestDataIfEnabled() {
        if (TestDataGenerationConfig.isRegenerationEnabled()) {
            LOG.info("Test data regeneration enabled - generating all test data");
            try {
                generateTikaTestData();
                generateChunkerTestData();
                generateEmbedderTestData();
            } catch (Exception e) {
                LOG.error("Error generating test data", e);
                throw new RuntimeException("Test data generation failed", e);
            }
        } else {
            LOG.debug("Test data regeneration disabled - skipping generation");
        }
    }

    /**
     * Generates tika-specific test data (requests and responses).
     */
    public static void generateTikaTestData() throws IOException {
        LOG.info("Generating Tika test data...");

        // Create directories
        String tikaRequestsPath = getTestDataPath("tika/requests");
        String tikaResponsesPath = getTestDataPath("tika/responses");

        createDirectoryIfNotExists(tikaRequestsPath);
        createDirectoryIfNotExists(tikaResponsesPath);

        // Clean existing files if regenerating
        if (TestDataGenerationConfig.isRegenerationEnabled()) {
            cleanDirectory(tikaRequestsPath);
            cleanDirectory(tikaResponsesPath);
        }

        // Generate tika request data (PipeStreams with document blobs)
        TikaTestDataGenerator.generateTikaRequests(tikaRequestsPath);

        // Generate tika response data (PipeDocs with extracted text)
        TikaTestDataGenerator.generateTikaResponses(tikaResponsesPath);

        LOG.info("Completed Tika test data generation");
    }

    /**
     * Gets the full path for test data within the test-utilities module.
     */
    public static String getTestDataPath(String relativePath) {
        return TEST_DATA_BASE_PATH + "/" + relativePath;
    }

    /**
     * Creates a directory if it doesn't exist.
     */
    public static void createDirectoryIfNotExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            LOG.debug("Created directory: {}", directoryPath);
        }
    }

    /**
     * Cleans all .bin files from a directory.
     */
    public static void cleanDirectory(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (Files.exists(path) && Files.isDirectory(path)) {
            Files.walk(path)
                .filter(Files::isRegularFile)
                .filter(file -> file.toString().endsWith(".bin"))
                .forEach(file -> {
                    try {
                        Files.delete(file);
                        LOG.debug("Deleted old test data file: {}", file);
                    } catch (IOException e) {
                        LOG.warn("Failed to delete file: {}", file, e);
                    }
                });
        }
    }

    /**
     * Saves a collection of PipeDoc objects to disk with a specific prefix.
     */
    public static void savePipeDocsToDirectory(Collection<PipeDoc> documents, String directoryPath, String prefix) {
        try {
            createDirectoryIfNotExists(directoryPath);
            String fullPrefix = directoryPath + "/" + prefix + "_";
            ProtobufUtils.saveProtobufsToDisk(fullPrefix, documents);
            LOG.info("Saved {} PipeDoc files to {} with prefix {}", documents.size(), directoryPath, prefix);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save PipeDoc files", e);
        }
    }

    /**
     * Saves a collection of PipeStream objects to disk with a specific prefix.
     */
    public static void savePipeStreamsToDirectory(Collection<PipeStream> streams, String directoryPath, String prefix) {
        try {
            createDirectoryIfNotExists(directoryPath);
            String fullPrefix = directoryPath + "/" + prefix + "_";
            ProtobufUtils.saveProtobufsToDisk(fullPrefix, streams);
            LOG.info("Saved {} PipeStream files to {} with prefix {}", streams.size(), directoryPath, prefix);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save PipeStream files", e);
        }
    }

    /**
     * Loads PipeDoc objects from a directory.
     */
    public static List<PipeDoc> loadPipeDocsFromDirectory(String directoryPath) {
        try {
            return ProtobufUtils.loadPipeDocsFromDirectory(directoryPath, ".bin");
        } catch (IOException e) {
            LOG.warn("Failed to load PipeDoc files from {}: {}", directoryPath, e.getMessage());
            return List.of();
        }
    }

    /**
     * Loads PipeStream objects from a directory.
     */
    public static List<PipeStream> loadPipeStreamsFromDirectory(String directoryPath) {
        try {
            return ProtobufUtils.loadPipeStreamsFromDirectory(directoryPath, ".bin");
        } catch (IOException e) {
            LOG.warn("Failed to load PipeStream files from {}: {}", directoryPath, e.getMessage());
            return List.of();
        }
    }

    /**
     * Generates chunker-specific test data (input and output).
     */
    public static void generateChunkerTestData() throws IOException {
        LOG.info("Generating Chunker test data...");

        // Create directories
        String chunkerInputPath = getTestDataPath("chunker/input");
        String chunkerOutputPath = getTestDataPath("chunker/output");

        createDirectoryIfNotExists(chunkerInputPath);
        createDirectoryIfNotExists(chunkerOutputPath);

        // Clean existing files if regenerating
        if (TestDataGenerationConfig.isRegenerationEnabled()) {
            cleanDirectory(chunkerInputPath);
            cleanDirectory(chunkerOutputPath);
        }

        // Generate chunker input data (PipeDocs ready for chunking)
        PipeStreamDataGenerator.generateChunkerInputData(chunkerInputPath);

        // Generate chunker output data (PipeDocs with chunks)
        PipeStreamDataGenerator.generateChunkerOutputData(chunkerOutputPath);

        LOG.info("Completed Chunker test data generation");
    }

    /**
     * Generates embedder-specific test data (input and output).
     */
    public static void generateEmbedderTestData() throws IOException {
        LOG.info("Generating Embedder test data...");

        // Create directories
        String embedderInputPath = getTestDataPath("embedder/input");
        String embedderOutputPath = getTestDataPath("embedder/output");

        createDirectoryIfNotExists(embedderInputPath);
        createDirectoryIfNotExists(embedderOutputPath);

        // Clean existing files if regenerating
        if (TestDataGenerationConfig.isRegenerationEnabled()) {
            cleanDirectory(embedderInputPath);
            cleanDirectory(embedderOutputPath);
        }

        // Generate embedder input data (PipeDocs with chunks ready for embedding)
        PipeStreamDataGenerator.generateEmbedderInputData(embedderInputPath);

        // Generate embedder output data (PipeDocs with embeddings)
        PipeStreamDataGenerator.generateEmbedderOutputData(embedderOutputPath);

        LOG.info("Completed Embedder test data generation");
    }
}
