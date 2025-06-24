package com.rokkon.pipeline.chunker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.*;
import com.rokkon.pipeline.utils.ProcessingBuffer;
import com.rokkon.search.sdk.*;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chunker gRPC service implementation using Quarkus reactive patterns with Mutiny.
 * This service receives documents through gRPC and processes them by breaking them
 * into smaller, overlapping chunks for further processing.
 */
@GrpcService
@Singleton
public class ChunkerServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(ChunkerServiceImpl.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OverlapChunker overlapChunker;

    @Inject
    ChunkMetadataExtractor metadataExtractor;

    @Inject
    ProcessingBuffer<PipeDoc> outputBuffer;

    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        if (request == null) {
            LOG.error("Received null request");
            return Uni.createFrom().item(createErrorResponse("Request cannot be null", null));
        }

        // Use the internal method with isTest=false
        return processDataInternal(request, false);
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                ServiceRegistrationResponse registration = ServiceRegistrationResponse.newBuilder()
                        .setModuleName("chunker-module")
                        .setJsonConfigSchema(ChunkerOptions.getJsonV7Schema())
                        .build();

                LOG.info("Returned service registration for chunker module");
                return registration;

            } catch (Exception e) {
                LOG.error("Error getting service registration", e);
                return ServiceRegistrationResponse.newBuilder()
                    .setModuleName("chunker-module")
                    .build();
            }
        });
    }

    @Override
    public Uni<ProcessResponse> testProcessData(ProcessRequest request) {
        LOG.info("TestProcessData called - executing test version of chunker processing");

        // For test processing, we use the same logic as processData but:
        // 1. Don't write to any output buffers
        // 2. Add a test marker to the logs
        // 3. Use a test document if none provided

        if (request == null || !request.hasDocument()) {
            // Create a test document for validation
            PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc-" + System.currentTimeMillis())
                .setBody("This is a test document for chunker validation. It contains enough text to be chunked into multiple pieces. " +
                         "The chunker will process this text and create overlapping chunks according to the configuration. " +
                         "This helps verify that the chunker module is functioning correctly.")
                .build();

            ServiceMetadata testMetadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("test-step")
                .build();

            ProcessConfiguration testConfig = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                    .putFields("source_field", com.google.protobuf.Value.newBuilder()
                        .setStringValue("body").build())
                    .putFields("chunk_size", com.google.protobuf.Value.newBuilder()
                        .setNumberValue(50).build())
                    .putFields("overlap_size", com.google.protobuf.Value.newBuilder()
                        .setNumberValue(10).build())
                    .build())
                .build();

            request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(testMetadata)
                .setConfig(testConfig)
                .build();
        }

        // Process using regular logic but without side effects
        return processDataInternal(request, true);
    }

    private Uni<ProcessResponse> processDataInternal(ProcessRequest request, boolean isTest) {
        return Uni.createFrom().item(() -> {
            try {
                // Same processing logic as processData
                PipeDoc inputDoc = request.getDocument();
                ProcessConfiguration config = request.getConfig();
                ServiceMetadata metadata = request.getMetadata();
                String streamId = metadata.getStreamId();
                String pipeStepName = metadata.getPipeStepName();

                String logPrefix = isTest ? "[TEST] " : "";
                LOG.infof("%sProcessing document ID: %s for step: %s in stream: %s", 
                    logPrefix, 
                    inputDoc != null && inputDoc.getId() != null ? inputDoc.getId() : "unknown", 
                    pipeStepName, streamId);

                ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder();
                PipeDoc.Builder outputDocBuilder = inputDoc != null ? inputDoc.toBuilder() : PipeDoc.newBuilder();

                // If there's no document in non-test mode, return success but with a log message
                if (!isTest && !request.hasDocument()) {
                    LOG.info("No document provided in request");
                    return ProcessResponse.newBuilder()
                            .setSuccess(true)
                            .addProcessorLogs("Chunker service: no document to process. Chunker service successfully processed request.")
                            .build();
                }

                // Parse chunker options
                ChunkerOptions chunkerOptions;
                Struct customJsonConfig = config.getCustomJsonConfig();
                if (customJsonConfig != null && customJsonConfig.getFieldsCount() > 0) {
                    chunkerOptions = objectMapper.readValue(
                            JsonFormat.printer().print(customJsonConfig),
                            ChunkerOptions.class
                    );
                } else {
                    chunkerOptions = new ChunkerOptions();
                }

                if (chunkerOptions.sourceField() == null || chunkerOptions.sourceField().isEmpty()) {
                    return createErrorResponse("Missing 'source_field' in ChunkerOptions", null);
                }

                // Create chunks
                ChunkingResult chunkingResult = overlapChunker.createChunks(inputDoc, chunkerOptions, streamId, pipeStepName);
                List<Chunk> chunkRecords = chunkingResult.chunks();

                if (!chunkRecords.isEmpty()) {
                    Map<String, String> placeholderToUrlMap = chunkingResult.placeholderToUrlMap();
                    SemanticProcessingResult.Builder newSemanticResultBuilder = SemanticProcessingResult.newBuilder()
                            .setResultId(UUID.randomUUID().toString())
                            .setSourceFieldName(chunkerOptions.sourceField())
                            .setChunkConfigId(chunkerOptions.chunkConfigId());

                    String resultSetName = String.format(
                            chunkerOptions.resultSetNameTemplate(),
                            pipeStepName,
                            chunkerOptions.chunkConfigId()
                    ).replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    newSemanticResultBuilder.setResultSetName(resultSetName);

                    int currentChunkNumber = 0;
                    for (Chunk chunkRecord : chunkRecords) {
                        // Sanitize the chunk text to ensure valid UTF-8
                        String sanitizedText = UnicodeSanitizer.sanitizeInvalidUnicode(chunkRecord.text());

                        ChunkEmbedding.Builder chunkEmbeddingBuilder = ChunkEmbedding.newBuilder()
                                .setTextContent(sanitizedText)
                                .setChunkId(chunkRecord.id())
                                .setOriginalCharStartOffset(chunkRecord.originalIndexStart())
                                .setOriginalCharEndOffset(chunkRecord.originalIndexEnd())
                                .setChunkConfigId(chunkerOptions.chunkConfigId());

                        boolean containsUrlPlaceholder = (chunkerOptions.preserveUrls() != null && chunkerOptions.preserveUrls()) &&
                                !placeholderToUrlMap.isEmpty() &&
                                placeholderToUrlMap.keySet().stream().anyMatch(ph -> chunkRecord.text().contains(ph));

                        Map<String, com.google.protobuf.Value> extractedMetadata = metadataExtractor.extractAllMetadata(
                                sanitizedText,
                                currentChunkNumber,
                                chunkRecords.size(),
                                containsUrlPlaceholder
                        );

                        SemanticChunk.Builder semanticChunkBuilder = SemanticChunk.newBuilder()
                                .setChunkId(chunkRecord.id())
                                .setChunkNumber(currentChunkNumber)
                                .setEmbeddingInfo(chunkEmbeddingBuilder.build())
                                .putAllMetadata(extractedMetadata);

                        newSemanticResultBuilder.addChunks(semanticChunkBuilder.build());
                        currentChunkNumber++;
                    }
                    outputDocBuilder.addSemanticResults(newSemanticResultBuilder.build());

                    String successMessage = isTest ? 
                        String.format("%s%sSuccessfully created and added metadata to %d chunks for testing. Chunker service validated successfully.",
                            logPrefix, chunkerOptions.logPrefix(), chunkRecords.size()) :
                        String.format("%s%sSuccessfully created and added metadata to %d chunks from source field '%s' into result set '%s'. Chunker service successfully processed document.",
                            logPrefix, chunkerOptions.logPrefix(), chunkRecords.size(), chunkerOptions.sourceField(), resultSetName);

                    responseBuilder.addProcessorLogs(successMessage);
                } else {
                    responseBuilder.addProcessorLogs(String.format("%s%sNo content in '%s' to chunk for document ID: %s",
                            logPrefix, chunkerOptions.logPrefix(), chunkerOptions.sourceField(), inputDoc.getId()));
                }

                responseBuilder.setSuccess(true);
                PipeDoc outputDoc = outputDocBuilder.build();
                responseBuilder.setOutputDoc(outputDoc);

                // IMPORTANT: Don't add to buffer if this is a test
                if (!isTest) {
                    outputBuffer.add(outputDoc);
                }

                return responseBuilder.build();

            } catch (Exception e) {
                String errorMessage = String.format("Error in ChunkerService test: %s", e.getMessage());
                LOG.error(errorMessage, e);
                return createErrorResponse(errorMessage, e);
            }
        });
    }

    private ProcessResponse createErrorResponse(String errorMessage, Exception e) {
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder();
        responseBuilder.setSuccess(false);
        responseBuilder.addProcessorLogs(errorMessage);

        Struct.Builder errorDetailsBuilder = Struct.newBuilder();
        errorDetailsBuilder.putFields("error_message", com.google.protobuf.Value.newBuilder().setStringValue(errorMessage).build());
        if (e != null) {
            errorDetailsBuilder.putFields("error_type", com.google.protobuf.Value.newBuilder().setStringValue(e.getClass().getName()).build());
            if (e.getCause() != null) {
                errorDetailsBuilder.putFields("error_cause", com.google.protobuf.Value.newBuilder().setStringValue(e.getCause().getMessage()).build());
            }
        }
        responseBuilder.setErrorDetails(errorDetailsBuilder.build());
        return responseBuilder.build();
    }
}
