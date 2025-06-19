package com.rokkon.pipeline.engine.grpc;

import com.rokkon.search.engine.PipeStreamEngine;
import com.rokkon.search.engine.ProcessResponse;
import com.rokkon.search.engine.ProcessStatus;
import com.rokkon.search.model.PipeStream;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * gRPC implementation of the PipeStreamEngine service.
 * This is the main entry point for external clients to submit documents for processing.
 */
@GrpcService
@Singleton
public class PipeStreamEngineImpl implements PipeStreamEngine {

    private static final Logger LOG = LoggerFactory.getLogger(PipeStreamEngineImpl.class);

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

        // For now, just return a success response
        return Uni.createFrom().item(ProcessResponse.newBuilder()
                .setStreamId(request.getStreamId())
                .setStatus(ProcessStatus.ACCEPTED)
                .setMessage("Pipeline processing started")
                .setRequestId(UUID.randomUUID().toString())
                .setTimestamp(System.currentTimeMillis())
                .build());
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