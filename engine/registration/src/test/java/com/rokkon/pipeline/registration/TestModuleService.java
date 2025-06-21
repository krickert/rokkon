package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceRegistrationData;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Simple KISS test service that implements PipeStepProcessor
 * Just like echo module but designed for testing registration
 */
@GrpcService
@Singleton  
public class TestModuleService implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(TestModuleService.class);
    
    @Inject
    Event<TestModuleEvent> eventPublisher;
    
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.infof("🔧 Test module processing document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");
        
        // Fire event when processing starts
        eventPublisher.fire(new TestModuleEvent("PROCESSING_STARTED", 
            request.hasDocument() ? request.getDocument().getId() : "no-document"));
        
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Test module successfully processed document");
        
        if (request.hasDocument()) {
            // Echo the document back unchanged
            responseBuilder.setOutputDoc(request.getDocument());
        }
        
        LOG.debug("✅ Test module returning success");
        
        // Fire event when processing completes
        eventPublisher.fire(new TestModuleEvent("PROCESSING_COMPLETED", 
            request.hasDocument() ? request.getDocument().getId() : "no-document"));
        
        return Uni.createFrom().item(responseBuilder.build());
    }
    
    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        LOG.debug("📋 Test module providing registration data");
        
        ServiceRegistrationData response = ServiceRegistrationData.newBuilder()
            .setModuleName("test-module")
            .setJsonConfigSchema("{\"type\": \"object\", \"properties\": {\"testParam\": {\"type\": \"string\", \"description\": \"Test parameter for validation\"}}}")
            .build();
            
        return Uni.createFrom().item(response);
    }
}