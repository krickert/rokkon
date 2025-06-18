package com.rokkon.search.engine;

import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Example service showing how the engine could proxy requests
 * to different modules (same interface, different ports).
 * 
 * This demonstrates Quarkus's ability to have multiple gRPC clients
 * for the same service interface pointing to different endpoints.
 */
@ApplicationScoped
public class ModuleProxyService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModuleProxyService.class);
    
    // Multiple clients for the same gRPC service interface
    // Each points to a different module on a different port
    
    @GrpcClient("chunker-module")
    PipeStepProcessorGrpc.PipeStepProcessorBlockingStub chunkerClient;
    
    @GrpcClient("embedder-module") 
    PipeStepProcessorGrpc.PipeStepProcessorBlockingStub embedderClient;
    
    @GrpcClient("echo-module")
    PipeStepProcessorGrpc.PipeStepProcessorBlockingStub echoClient;
    
    /**
     * Route a request to the chunker module
     */
    public ProcessResponse processWithChunker(ProcessRequest request) {
        LOG.info("Routing request to chunker module on port 50051");
        return chunkerClient
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .processData(request);
    }
    
    /**
     * Route a request to the embedder module  
     */
    public ProcessResponse processWithEmbedder(ProcessRequest request) {
        LOG.info("Routing request to embedder module on port 50052");
        return embedderClient
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .processData(request);
    }
    
    /**
     * Route a request to the echo module
     */
    public ProcessResponse processWithEcho(ProcessRequest request) {
        LOG.info("Routing request to echo module on port 50053");
        return echoClient
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .processData(request);
    }
    
    /**
     * Example pipeline: document → chunker → embedder → echo
     */
    public ProcessResponse executeFullPipeline(ProcessRequest initialRequest) {
        LOG.info("Executing full pipeline across multiple modules");
        
        // Step 1: Chunker
        ProcessResponse chunkedResult = processWithChunker(initialRequest);
        
        // Step 2: Embedder (using chunker's output)
        ProcessRequest embedRequest = ProcessRequest.newBuilder()
                .setDocument(chunkedResult.getOutputDoc())
                .setConfig(initialRequest.getConfig())
                .setMetadata(initialRequest.getMetadata())
                .build();
        ProcessResponse embeddedResult = processWithEmbedder(embedRequest);
        
        // Step 3: Echo (final step)
        ProcessRequest echoRequest = ProcessRequest.newBuilder()
                .setDocument(embeddedResult.getOutputDoc())
                .setConfig(initialRequest.getConfig()) 
                .setMetadata(initialRequest.getMetadata())
                .build();
        
        return processWithEcho(echoRequest);
    }
}