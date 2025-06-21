package com.rokkon.echo;

import com.google.protobuf.Empty;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

@GrpcService
@Singleton
public class EchoServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(EchoServiceImpl.class);
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.debugf("Echo service received document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");
        
        // Simply echo back the document we received
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Echo service successfully processed document");
        
        // If there's a document, echo it back
        if (request.hasDocument()) {
            responseBuilder.setOutputDoc(request.getDocument());
        }
        
        ProcessResponse response = responseBuilder.build();
        
        LOG.debugf("Echo service returning success: %s", response.getSuccess());
        
        return Uni.createFrom().item(response);
    }

    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        LOG.debug("Echo service registration requested");
        
        ServiceRegistrationData response = ServiceRegistrationData.newBuilder()
                .setModuleName("echo-module")
                // No JSON schema for echo service - it accepts any input
                .build();
        
        return Uni.createFrom().item(response);
    }
    
    @Override
    public Uni<ProcessResponse> testProcessData(ProcessRequest request) {
        LOG.debug("TestProcessData called - proxying to processData");
        return processData(request);
    }
}