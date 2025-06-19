package com.rokkon.pipeline.chunker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.*;
import com.rokkon.pipeline.utils.ProcessingBuffer;
import com.rokkon.search.sdk.*;
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

        return Uni.createFrom().item(() -> {
            try {
                PipeDoc inputDoc = request.getDocument();
                ProcessConfiguration config = request.getConfig();
                ServiceMetadata metadata = request.getMetadata();
                String streamId = metadata.getStreamId();
                String pipeStepName = metadata.getPipeStepName();

                LOG.infof("Processing document ID: %s for step: %s in stream: %s", 
                    inputDoc != null && inputDoc.getId() != null ? inputDoc.getId() : "unknown", pipeStepName, streamId);

                ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder();
                PipeDoc.Builder outputDocBuilder = inputDoc != null ? inputDoc.toBuilder() : PipeDoc.newBuilder();

                // If there's no document, return success but with a log message
                if (!request.hasDocument()) {
                    LOG.info("No document provided in request");
                    return ProcessResponse.newBuilder()
                            .setSuccess(true)
                            .addProcessorLogs("Chunker service: no document to process. Chunker service successfully processed request.")
                            .build();
                }

                // Parse chunker options from the request configuration
                ChunkerOptions chunkerOptions;
                Struct customJsonConfig = config.getCustomJsonConfig();
                if (customJsonConfig != null && customJsonConfig.getFieldsCount() > 0) {
                    chunkerOptions = objectMapper.readValue(
                            JsonFormat.printer().print(customJsonConfig),
                            ChunkerOptions.class
                    );
                } else {
                    LOG.warnf("No custom JSON config provided for ChunkerService. Using defaults. streamId: %s, pipeStepName: %s", 
                        streamId, pipeStepName);
                    chunkerOptions = new ChunkerOptions();
                }

                if (chunkerOptions.sourceField() == null || chunkerOptions.sourceField().isEmpty()) {
                    return createErrorResponse("Missing 'source_field' in ChunkerOptions", null);
                }

                // Create chunks using the OverlapChunker
                ChunkingResult chunkingResult = overlapChunker.createChunks(inputDoc, chunkerOptions, streamId, pipeStepName);
                List<Chunk> chunkRecords = chunkingResult.chunks();
                Map<String, String> placeholderToUrlMap = chunkingResult.placeholderToUrlMap();

                if (!chunkRecords.isEmpty()) {
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
                                sanitizedText,  // Use sanitized text for metadata extraction too
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
                    responseBuilder.addProcessorLogs(String.format("%sSuccessfully created and added metadata to %d chunks from source field '%s' into result set '%s'. Chunker service successfully processed document.",
                            chunkerOptions.logPrefix(), chunkRecords.size(), chunkerOptions.sourceField(), resultSetName));

                } else {
                    responseBuilder.addProcessorLogs(String.format("%sNo content in '%s' to chunk for document ID: %s",
                            chunkerOptions.logPrefix(), chunkerOptions.sourceField(), inputDoc.getId()));
                }

                responseBuilder.setSuccess(true);
                PipeDoc outputDoc = outputDocBuilder.build();
                responseBuilder.setOutputDoc(outputDoc);

                // Add the document to the buffer - no need to check if enabled
                // If buffer is disabled, the NoOpProcessingBuffer will do nothing
                outputBuffer.add(outputDoc);

                ProcessResponse response = responseBuilder.build();
                LOG.debugf("Chunker service returning success: %s with %d chunks", 
                    response.getSuccess(), 
                    response.hasOutputDoc() && response.getOutputDoc().getSemanticResultsCount() > 0 ? 
                        response.getOutputDoc().getSemanticResults(0).getChunksCount() : 0);

                return response;

            } catch (Exception e) {
                String errorMessage = String.format("Error in ChunkerService: %s", e.getMessage());
                LOG.error(errorMessage, e);
                return createErrorResponse(errorMessage, e);
            }
        });
    }

    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        return Uni.createFrom().item(() -> {
            try {
                ServiceRegistrationData registration = ServiceRegistrationData.newBuilder()
                        .setModuleName("chunker-module")
                        .setJsonConfigSchema(ChunkerOptions.getJsonV7Schema())
                        .build();

                LOG.info("Returned service registration for chunker module");
                return registration;

            } catch (Exception e) {
                LOG.error("Error getting service registration", e);
                return ServiceRegistrationData.newBuilder()
                    .setModuleName("chunker-module")
                    .build();
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
