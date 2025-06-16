package com.rokkon.modules.echo;

import com.rokkon.search.sdk.PipeStepProcessorGrpc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceRegistrationData;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple echo service that implements PipeStepProcessor.
 * This service echoes back whatever PipeDoc it receives - perfect for testing
 * pipeline flows and engine integration without complex processing logic.
 */
@GrpcService
public class EchoService extends PipeStepProcessorGrpc.PipeStepProcessorImplBase {
    
    private static final Logger LOG = LoggerFactory.getLogger(EchoService.class);
    
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.debug("Echo service received document: {}", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");
        
        // Simply echo back the document we received
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Echo service processed document successfully");
        
        // If there's a document, echo it back
        if (request.hasDocument()) {
            responseBuilder.setOutputDoc(request.getDocument());
        }
        
        ProcessResponse response = responseBuilder.build();
        
        LOG.debug("Echo service returning success: {}", response.getSuccess());
        
        return Uni.createFrom().item(response);
    }
    
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        LOG.debug("Echo service registration requested");
        
        ServiceRegistrationData response = ServiceRegistrationData.newBuilder()
                .setModuleName("echo-service")
                // No JSON schema for echo service - it accepts any input
                .build();
        
        return Uni.createFrom().item(response);
    }
}