package com.rokkon.pipeline.engine.grpc;

import com.rokkon.pipeline.engine.service.PipelineExecutorService;
import com.rokkon.search.engine.MutinyPipeStreamEngineGrpc;
import com.rokkon.search.engine.ProcessResponse;
import com.rokkon.search.engine.ProcessStatus;
import com.rokkon.search.model.PipeStream;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * gRPC implementation of the PipeStreamEngine service.
 * This is the main entry point for external clients to submit documents for processing.
 */
@GrpcService
@Singleton
public class PipeStreamEngineImpl extends MutinyPipeStreamEngineGrpc.PipeStreamEngineImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(PipeStreamEngineImpl.class);
    
    @Inject
    PipelineExecutorService pipelineExecutor;

    @PostConstruct
    void init() {
        LOG.info("PipeStreamEngineImpl gRPC service initialized - CDI bean created successfully");
    }

    @Override
    public Uni<PipeStream> testPipeStream(PipeStream request) {
        LOG.debug("Test pipe stream called with streamId: {}", request.getStreamId());
        
        // Simple echo test implementation
        return Uni.createFrom().item(request.toBuilder()
                .putContextParams("test_processed", "true")
                .putContextParams("test_timestamp", String.valueOf(System.currentTimeMillis()))
                .build());
    }

    @Override
    public Uni<ProcessResponse> processPipeAsync(PipeStream request) {
        LOG.info("Processing pipe async: streamId={}, pipeline={}", 
                request.getStreamId(), request.getCurrentPipelineName());

        // Extract pipeline name and document
        String pipelineName = request.getCurrentPipelineName();
        if (pipelineName == null || pipelineName.isBlank()) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("Pipeline name is required in PipeStream")
            );
        }
        
        if (!request.hasDocument()) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("Document is required in PipeStream")
            );
        }
        
        // Execute the pipeline
        return pipelineExecutor.executePipeline(
            pipelineName, 
            request.getDocument(),
            request.getActionType()
        )
        .onFailure().transform(error -> {
            LOG.error("Pipeline execution failed for stream {}", request.getStreamId(), error);
            return new RuntimeException("Pipeline execution failed: " + error.getMessage(), error);
        });
    }

    @Override
    public Multi<ProcessResponse> processPipeStream(Multi<PipeStream> request) {
        LOG.debug("Processing pipe stream (streaming mode)");
        
        // Process each incoming PipeStream
        return request.onItem().transformToUniAndConcatenate(pipeStream -> {
            LOG.debug("Processing stream item: {}", pipeStream.getStreamId());
            
            // Reuse the async processing logic
            return processPipeAsync(pipeStream);
        });
    }
}