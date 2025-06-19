package com.rokkon.modules.chunker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import com.rokkon.search.model.ChunkEmbedding;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.SemanticChunk;
import com.rokkon.search.model.SemanticProcessingResult;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chunker gRPC service implementation using Quarkus reactive patterns with Mutiny.
 * This service receives documents through gRPC and processes them using OverlapChunker.
 * 
 * Uses Uni<> reactive types for better Quarkus integration and testability.
 */
@GrpcService
public class ChunkerService extends PipeStepProcessorGrpc.PipeStepProcessorImplBase {

    private static final Logger log = LoggerFactory.getLogger(ChunkerService.class);
    
    @Inject
    ObjectMapper objectMapper;
    
    @Inject
    OverlapChunker overlapChunker;
    
    @Inject
    ChunkMetadataExtractor metadataExtractor;

    @RunOnVirtualThread
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        if (request == null) {
            log.error("Received null request");
            return Uni.createFrom().item(createErrorResponse("Request cannot be null", null));
        }
        
        return Uni.createFrom().item(() -> {
            try {
                
                PipeDoc inputDoc = request.getDocument();
                ProcessConfiguration config = request.getConfig();
                ServiceMetadata metadata = request.getMetadata();
                String streamId = metadata.getStreamId();
                String pipeStepName = metadata.getPipeStepName();

                ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder();
                PipeDoc.Builder outputDocBuilder = inputDoc.toBuilder();

                log.info("Processing document ID: {} for step: {} in stream: {}", inputDoc.getId(), pipeStepName, streamId);

                Struct customJsonConfig = config.getCustomJsonConfig();
                ChunkerOptions chunkerOptions;
                if (customJsonConfig != null && customJsonConfig.getFieldsCount() > 0) {
                    chunkerOptions = objectMapper.readValue(
                            JsonFormat.printer().print(customJsonConfig),
                            ChunkerOptions.class
                    );
                } else {
                    log.warn("No custom JSON config provided for ChunkerService. Using defaults. streamId: {}, pipeStepName: {}", streamId, pipeStepName);
                    chunkerOptions = new ChunkerOptions();
                }

                if (chunkerOptions.sourceField() == null || chunkerOptions.sourceField().isEmpty()) {
                    return createErrorResponse("Missing 'source_field' in ChunkerOptions", null);
                }

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
                        ChunkEmbedding.Builder chunkEmbeddingBuilder = ChunkEmbedding.newBuilder()
                                .setTextContent(chunkRecord.text())
                                .setChunkId(chunkRecord.id())
                                .setOriginalCharStartOffset(chunkRecord.originalIndexStart())
                                .setOriginalCharEndOffset(chunkRecord.originalIndexEnd())
                                .setChunkConfigId(chunkerOptions.chunkConfigId());

                        boolean containsUrlPlaceholder = (chunkerOptions.preserveUrls() != null && chunkerOptions.preserveUrls()) &&
                                !placeholderToUrlMap.isEmpty() &&
                                placeholderToUrlMap.keySet().stream().anyMatch(ph -> chunkRecord.text().contains(ph));

                        Map<String, Value> extractedMetadata = metadataExtractor.extractAllMetadata(
                                chunkRecord.text(),
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
                    responseBuilder.addProcessorLogs(String.format("%sSuccessfully created and added metadata to %d chunks from source field '%s' into result set '%s'.",
                            chunkerOptions.logPrefix(), chunkRecords.size(), chunkerOptions.sourceField(), resultSetName));

                } else {
                    responseBuilder.addProcessorLogs(String.format("%sNo content in '%s' to chunk for document ID: %s",
                            chunkerOptions.logPrefix(), chunkerOptions.sourceField(), inputDoc.getId()));
                }

                responseBuilder.setSuccess(true);
                responseBuilder.setOutputDoc(outputDocBuilder.build());
                return responseBuilder.build();

            } catch (Exception e) {
                String errorMessage = String.format("Error in ChunkerService for step %s, stream %s: %s", 
                    request.getMetadata().getPipeStepName(), request.getMetadata().getStreamId(), e.getMessage());
                log.error(errorMessage, e);
                return createErrorResponse(errorMessage, e);
            }
        });
    }

    @RunOnVirtualThread
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        return Uni.createFrom().item(() -> {
            try {
                ServiceRegistrationData registration = ServiceRegistrationData.newBuilder()
                        .setModuleName("chunker")
                        .setJsonConfigSchema(ChunkerOptions.getJsonV7Schema())
                        .build();
                
                log.info("Returned service registration for chunker module");
                return registration;
                
            } catch (Exception e) {
                log.error("Error getting service registration", e);
                return ServiceRegistrationData.newBuilder()
                    .setModuleName("chunker")
                    .build();
            }
        });
    }

    private ProcessResponse createErrorResponse(String errorMessage, Exception e) {
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder();
        responseBuilder.setSuccess(false);
        responseBuilder.addProcessorLogs(errorMessage);

        Struct.Builder errorDetailsBuilder = Struct.newBuilder();
        errorDetailsBuilder.putFields("error_message", Value.newBuilder().setStringValue(errorMessage).build());
        if (e != null) {
            errorDetailsBuilder.putFields("error_type", Value.newBuilder().setStringValue(e.getClass().getName()).build());
            if (e.getCause() != null) {
                errorDetailsBuilder.putFields("error_cause", Value.newBuilder().setStringValue(e.getCause().getMessage()).build());
            }
        }
        responseBuilder.setErrorDetails(errorDetailsBuilder.build());
        return responseBuilder.build();
    }
}