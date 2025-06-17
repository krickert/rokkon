package com.rokkon.test.generation;

import com.rokkon.search.model.Blob;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.model.SemanticChunk;
import com.rokkon.search.model.SemanticProcessingResult;
import com.rokkon.search.model.ChunkEmbedding;
import com.rokkon.test.config.TestDataGenerationConfig;
import com.rokkon.test.data.TestDocumentLoader;
import com.rokkon.test.protobuf.ProtobufUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates test data for pipeline processing.
 * Creates both input and output data for various pipeline stages (chunker, embedder, etc.).
 */
public class PipeStreamDataGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(PipeStreamDataGenerator.class);

    /**
     * Generates chunker input test data (PipeDocs ready for chunking).
     * These represent the input to the chunker processing.
     */
    public static void generateChunkerInputData(String outputDirectory) throws IOException {
        LOG.info("Generating chunker input test data...");

        // Use Tika processed documents as input for chunker
        List<PipeDoc> inputDocs = TestDocumentLoader.loadAllTikaTestDocuments();

        if (inputDocs.isEmpty()) {
            LOG.warn("No Tika processed documents found for chunker input. Generating sample documents.");
            // If no Tika documents are available, create some sample documents
            inputDocs = createSampleDocumentsForChunker();
        }

        String prefix = "chunker_input";
        TestDataGenerator.savePipeDocsToDirectory(inputDocs, outputDirectory, prefix);

        // Save output bin documents if enabled
        saveOutputBinDocuments(inputDocs, "chunker-input-docs");

        LOG.info("Generated {} chunker input PipeDocs", inputDocs.size());
    }

    /**
     * Generates chunker output test data (PipeDocs with chunks).
     * These represent the output of chunker processing.
     */
    public static void generateChunkerOutputData(String outputDirectory) throws IOException {
        LOG.info("Generating chunker output test data...");

        // Create chunked documents with different configurations
        List<PipeDoc> outputDocs = createChunkedDocuments();

        String prefix = "chunker_output";
        TestDataGenerator.savePipeDocsToDirectory(outputDocs, outputDirectory, prefix);

        // Save output bin documents if enabled
        saveOutputBinDocuments(outputDocs, "chunker-output-docs");

        LOG.info("Generated {} chunker output PipeDocs", outputDocs.size());
    }

    /**
     * Generates embedder input test data (PipeDocs with chunks ready for embedding).
     * These represent the input to the embedder processing.
     */
    public static void generateEmbedderInputData(String outputDirectory) throws IOException {
        LOG.info("Generating embedder input test data...");

        // Use chunked documents as input for embedder
        List<PipeDoc> inputDocs = createChunkedDocuments();

        String prefix = "embedder_input";
        TestDataGenerator.savePipeDocsToDirectory(inputDocs, outputDirectory, prefix);

        // Save output bin documents if enabled
        saveOutputBinDocuments(inputDocs, "embedder-input-docs");

        LOG.info("Generated {} embedder input PipeDocs", inputDocs.size());
    }

    /**
     * Generates embedder output test data (PipeDocs with embeddings).
     * These represent the output of embedder processing.
     */
    public static void generateEmbedderOutputData(String outputDirectory) throws IOException {
        LOG.info("Generating embedder output test data...");

        // Create documents with embeddings
        List<PipeDoc> outputDocs = createDocumentsWithEmbeddings();

        String prefix = "embedder_output";
        TestDataGenerator.savePipeDocsToDirectory(outputDocs, outputDirectory, prefix);

        // Save output bin documents if enabled
        saveOutputBinDocuments(outputDocs, "embedder-output-docs");

        LOG.info("Generated {} embedder output PipeDocs", outputDocs.size());
    }

    /**
     * Creates sample documents for chunker testing when no Tika documents are available.
     */
    private static List<PipeDoc> createSampleDocumentsForChunker() {
        List<PipeDoc> documents = new ArrayList<>();

        // Create 5 sample documents with different content
        for (int i = 0; i < 5; i++) {
            String docId = "sample-doc-" + String.format("%03d", i);

            PipeDoc doc = PipeDoc.newBuilder()
                    .setId(docId)
                    .setTitle("Sample Document " + i)
                    .setBody(generateSampleText(i, 2000)) // Generate 2000 characters of text
                    .setProcessedDate(ProtobufUtils.now())
                    .build();

            documents.add(doc);
        }

        return documents;
    }

    /**
     * Creates documents with chunks for testing chunker output and embedder input.
     */
    private static List<PipeDoc> createChunkedDocuments() {
        List<PipeDoc> documents = new ArrayList<>();

        // Get some sample documents to chunk
        List<PipeDoc> inputDocs = TestDocumentLoader.loadAllTikaTestDocuments();
        if (inputDocs.isEmpty()) {
            inputDocs = createSampleDocumentsForChunker();
        }

        // Limit to first 5 documents for testing
        List<PipeDoc> docsToProcess = inputDocs.subList(0, Math.min(5, inputDocs.size()));

        // Create two versions of each document with different chunk configurations
        for (PipeDoc doc : docsToProcess) {
            // Create document with small chunks (150/40)
            PipeDoc smallChunkDoc = createDocumentWithChunks(doc, 150, 40, "small_chunks_150_40");
            documents.add(smallChunkDoc);

            // Create document with large chunks (500/100)
            PipeDoc largeChunkDoc = createDocumentWithChunks(doc, 500, 100, "large_chunks_500_100");
            documents.add(largeChunkDoc);
        }

        return documents;
    }

    /**
     * Creates a document with chunks based on the specified configuration.
     */
    private static PipeDoc createDocumentWithChunks(PipeDoc sourceDoc, int chunkSize, int chunkOverlap, String configId) {
        if (sourceDoc == null || !sourceDoc.hasBody() || sourceDoc.getBody().isEmpty()) {
            LOG.warn("Source document is null or has no body content");
            return sourceDoc;
        }

        String body = sourceDoc.getBody();
        List<String> chunks = createTextChunks(body, chunkSize, chunkOverlap);

        // Create semantic processing result with chunks
        SemanticProcessingResult.Builder resultBuilder = SemanticProcessingResult.newBuilder()
                .setResultId(UUID.randomUUID().toString())
                .setSourceFieldName("body")
                .setChunkConfigId(configId)
                .setResultSetName("test_chunks_" + configId);

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            String chunkId = sourceDoc.getId() + "_chunk_" + i;

            // Create chunk embedding info
            ChunkEmbedding embeddingInfo = ChunkEmbedding.newBuilder()
                    .setChunkId(chunkId)
                    .setTextContent(chunkText)
                    .setChunkConfigId(configId)
                    .setOriginalCharStartOffset(i * (chunkSize - chunkOverlap))
                    .setOriginalCharEndOffset(Math.min((i + 1) * chunkSize - i * chunkOverlap, body.length()))
                    .build();

            // Create semantic chunk
            SemanticChunk chunk = SemanticChunk.newBuilder()
                    .setChunkId(chunkId)
                    .setChunkNumber(i)
                    .setEmbeddingInfo(embeddingInfo)
                    .build();

            resultBuilder.addChunks(chunk);
        }

        // Create new document with chunks
        return sourceDoc.toBuilder()
                .addSemanticResults(resultBuilder.build())
                .build();
    }

    /**
     * Creates text chunks from a source text based on the specified configuration.
     */
    private static List<String> createTextChunks(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int textLength = text.length();
        int startIndex = 0;

        while (startIndex < textLength) {
            int endIndex = Math.min(startIndex + chunkSize, textLength);

            // Adjust end index to avoid cutting words
            if (endIndex < textLength) {
                while (endIndex > startIndex && !Character.isWhitespace(text.charAt(endIndex - 1))) {
                    endIndex--;
                }
            }

            // If we couldn't find a good break point, just use the original end index
            if (endIndex <= startIndex) {
                endIndex = Math.min(startIndex + chunkSize, textLength);
            }

            String chunk = text.substring(startIndex, endIndex).trim();
            chunks.add(chunk);

            // Move to next chunk with overlap
            startIndex = endIndex - chunkOverlap;

            // Ensure we make progress
            if (startIndex <= 0 || startIndex >= textLength - 1) {
                break;
            }
        }

        return chunks;
    }

    /**
     * Creates documents with embeddings for testing embedder output.
     */
    private static List<PipeDoc> createDocumentsWithEmbeddings() {
        // Start with chunked documents
        List<PipeDoc> chunkedDocs = createChunkedDocuments();
        List<PipeDoc> embeddedDocs = new ArrayList<>();

        // Add embeddings to each chunk
        for (PipeDoc doc : chunkedDocs) {
            PipeDoc.Builder docBuilder = doc.toBuilder();

            for (int i = 0; i < doc.getSemanticResultsCount(); i++) {
                SemanticProcessingResult result = doc.getSemanticResults(i);
                SemanticProcessingResult.Builder resultBuilder = result.toBuilder();

                for (int j = 0; j < result.getChunksCount(); j++) {
                    SemanticChunk chunk = result.getChunks(j);

                    // Add sample embedding vector to the chunk
                    ChunkEmbedding.Builder embeddingBuilder = chunk.getEmbeddingInfo().toBuilder();

                    // Add vector values
                    float[] vectorValues = generateSampleEmbeddingVector(384);
                    for (float value : vectorValues) {
                        embeddingBuilder.addVector(value);
                    }

                    ChunkEmbedding embeddingWithVector = embeddingBuilder.build();

                    // Update the chunk with the embedding
                    SemanticChunk updatedChunk = chunk.toBuilder()
                            .setEmbeddingInfo(embeddingWithVector)
                            .build();

                    resultBuilder.setChunks(j, updatedChunk);
                }

                docBuilder.setSemanticResults(i, resultBuilder.build());
            }

            embeddedDocs.add(docBuilder.build());
        }

        return embeddedDocs;
    }

    /**
     * Generates a sample embedding vector of the specified dimension.
     */
    private static float[] generateSampleEmbeddingVector(int dimension) {
        // Create a simple float array with random values
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) Math.random() * 2 - 1; // Random value between -1 and 1
        }

        return vector;
    }

    /**
     * Generates sample text of the specified length for testing.
     */
    private static String generateSampleText(int seed, int length) {
        String[] paragraphs = {
            "This is a sample document for testing chunking and embedding. It contains multiple paragraphs with varied content to ensure proper testing of text processing algorithms.",
            "The chunker module is responsible for breaking documents into smaller, semantically meaningful chunks. These chunks are then processed by the embedder to generate vector representations.",
            "Proper chunking is essential for effective document processing. Chunks should be sized appropriately to capture semantic meaning while remaining small enough for efficient processing.",
            "The overlap between chunks helps maintain context and ensures that concepts that span chunk boundaries are properly captured in the embeddings.",
            "Different chunking configurations can be used depending on the specific requirements of the application. Smaller chunks with less overlap might be more appropriate for some use cases, while larger chunks with more overlap might be better for others.",
            "Testing with various document types and content structures helps ensure that the chunking and embedding processes work correctly across a wide range of inputs.",
            "This sample text is designed to provide enough content for meaningful chunking tests while being generic enough to be used in various test scenarios."
        };

        StringBuilder sb = new StringBuilder();
        int index = seed % paragraphs.length;

        while (sb.length() < length) {
            sb.append(paragraphs[index]).append("\n\n");
            index = (index + 1) % paragraphs.length;
        }

        return sb.substring(0, Math.min(sb.length(), length));
    }

    /**
     * Saves output bin documents to the data loader directory if enabled.
     * This is controlled by the rokkon.test.data.save.output.bin system property.
     * 
     * @param documents The documents to save
     * @param subdirectory The subdirectory within the data loader directory to save to
     */
    private static void saveOutputBinDocuments(List<PipeDoc> documents, String subdirectory) {
        // Check if saving output bin documents is enabled
        if (!TestDataGenerationConfig.isSaveOutputBinEnabled()) {
            LOG.debug("Saving output bin documents is disabled. Set -Drokkon.test.data.save.output.bin=true to enable.");
            return;
        }

        if (documents == null || documents.isEmpty()) {
            LOG.warn("No documents to save as output bin documents");
            return;
        }

        try {
            // Create the output directory
            String outputDir = TestDataGenerationConfig.getOutputDirectory() + "/data-loader/" + subdirectory;
            TestDataGenerator.createDirectoryIfNotExists(outputDir);

            // Save each document
            for (int i = 0; i < documents.size(); i++) {
                PipeDoc doc = documents.get(i);
                String filename = String.format("%s_%03d_%s.bin", subdirectory, i, doc.getId());
                String filePath = outputDir + "/" + filename;

                try {
                    ProtobufUtils.saveProtobufToDisk(filePath, doc);
                    LOG.debug("Saved output bin document to {}", filePath);
                } catch (IOException e) {
                    LOG.error("Failed to save output bin document to {}: {}", filePath, e.getMessage());
                }
            }

            LOG.info("Saved {} output bin documents to {}", documents.size(), outputDir);
        } catch (Exception e) {
            LOG.error("Error saving output bin documents: {}", e.getMessage(), e);
        }
    }
}
