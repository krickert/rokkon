package com.rokkon.pipeline.engine.grpc;

import com.rokkon.pipeline.engine.service.PipelineExecutorService;
import com.rokkon.search.engine.MutinyConnectorEngineGrpc;
import com.rokkon.search.engine.ConnectorRequest;
import com.rokkon.search.engine.ConnectorResponse;
import com.rokkon.search.engine.ProcessStatus;
import com.rokkon.search.model.ActionType;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Implementation of the ConnectorEngine gRPC service.
 * This service receives documents from various connectors (Gutenberg, Wikipedia, etc.)
 * and routes them to the appropriate pipeline based on the connector type.
 */
@GrpcService
public class ConnectorEngineImpl extends MutinyConnectorEngineGrpc.ConnectorEngineImplBase {
    
    private static final Logger LOG = Logger.getLogger(ConnectorEngineImpl.class);
    
    @Inject
    PipelineExecutorService pipelineExecutor;
    
    @PostConstruct
    void init() {
        LOG.info("ConnectorEngineImpl gRPC service initialized - CDI bean created successfully");
    }
    
    @Override
    public Uni<ConnectorResponse> processConnectorDoc(ConnectorRequest request) {
        LOG.infof("Received document from connector: %s", request.getConnectorType());
        
        // Validate the request
        if (request.getConnectorType() == null || request.getConnectorType().isEmpty()) {
            return Uni.createFrom().item(ConnectorResponse.newBuilder()
                .setAccepted(false)
                .setMessage("Missing required field: connector_type")
                .build());
        }
        
        if (!request.hasDocument()) {
            return Uni.createFrom().item(ConnectorResponse.newBuilder()
                .setAccepted(false)
                .setMessage("Missing required field: document")
                .build());
        }
        
        // Generate stream ID (use suggested if provided, otherwise generate new)
        String streamId = request.hasSuggestedStreamId() && !request.getSuggestedStreamId().isEmpty()
            ? request.getSuggestedStreamId()
            : generateStreamId(request.getConnectorType());
        
        // Log batch info if present
        if (request.hasBatchInfo()) {
            LOG.debugf("Processing batch item %d/%d from batch: %s",
                request.getBatchInfo().getCurrentItemNumber(),
                request.getBatchInfo().getTotalItems(),
                request.getBatchInfo().getBatchId());
        }
        
        // Map connector type to pipeline name
        String pipelineName = mapConnectorTypeToPipeline(request.getConnectorType());
        
        // Execute the pipeline asynchronously
        return pipelineExecutor.executePipeline(
                pipelineName,
                request.getDocument(),
                ActionType.CREATE // Connectors always create new documents
            )
            .onItem().transform(response -> {
                if (response.getStatus() == ProcessStatus.ACCEPTED) {
                    LOG.infof("Successfully accepted document from %s with stream_id: %s",
                        request.getConnectorType(), response.getStreamId());
                    
                    return ConnectorResponse.newBuilder()
                        .setStreamId(response.getStreamId())
                        .setAccepted(true)
                        .setMessage(String.format(
                            "Document accepted for processing in pipeline '%s' with stream_id: %s",
                            pipelineName, response.getStreamId()))
                        .build();
                } else {
                    LOG.warnf("Pipeline rejected document from %s: %s",
                        request.getConnectorType(), response.getMessage());
                    
                    return ConnectorResponse.newBuilder()
                        .setStreamId(response.getStreamId())
                        .setAccepted(false)
                        .setMessage("Pipeline rejected document: " + response.getMessage())
                        .build();
                }
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to process document from connector %s",
                    request.getConnectorType());
                
                return ConnectorResponse.newBuilder()
                    .setStreamId(streamId)
                    .setAccepted(false)
                    .setMessage("Internal error: " + throwable.getMessage())
                    .build();
            });
    }
    
    /**
     * Map connector type to pipeline name.
     * This could be enhanced to read from configuration.
     */
    private String mapConnectorTypeToPipeline(String connectorType) {
        // TODO: This should be configurable via Consul
        return switch (connectorType.toLowerCase()) {
            case "gutenberg" -> "gutenberg-pipeline";
            case "wikipedia" -> "wikipedia-pipeline";
            case "arxiv" -> "academic-pipeline";
            case "gdelt" -> "news-pipeline";
            default -> "default-pipeline";
        };
    }
    
    /**
     * Generate a unique stream ID for tracking this document through the pipeline
     */
    private String generateStreamId(String connectorType) {
        return String.format("%s-%s-%s",
            connectorType,
            System.currentTimeMillis(),
            UUID.randomUUID().toString().substring(0, 8));
    }
}