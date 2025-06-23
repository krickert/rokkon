package com.rokkon.pipeline.engine.test;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;
import com.rokkon.search.sdk.RegistrationRequest;
import com.rokkon.search.sdk.ServiceRegistrationResponse;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Dummy processor for testing purposes.
 * This processor simply echoes back the input with a modification.
 */
@GrpcService
@Singleton
public class DummyProcessorService implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(DummyProcessorService.class);

    @ConfigProperty(name = "dummy.processor.name", defaultValue = "test-processor")
    String processorName;

    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.infof("DummyProcessor received request for document: %s", 
                  request.hasDocument() ? request.getDocument().getId() : "no-document");

        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("DummyProcessor: Processing document");

        if (request.hasDocument()) {
            // Echo the document back with a modification
            PipeDoc doc = request.getDocument();

            // Create or update custom_data with processing metadata
            Struct.Builder customDataBuilder = doc.hasCustomData() 
                ? doc.getCustomData().toBuilder() 
                : Struct.newBuilder();

            customDataBuilder
                .putFields("processed_by", Value.newBuilder().setStringValue(processorName).build())
                .putFields("processing_timestamp", Value.newBuilder().setStringValue(String.valueOf(System.currentTimeMillis())).build());

            PipeDoc modifiedDoc = doc.toBuilder()
                    .setCustomData(customDataBuilder.build())
                    .build();

            responseBuilder.setOutputDoc(modifiedDoc);
            responseBuilder.addProcessorLogs("DummyProcessor: Added metadata to document");
        }

        ProcessResponse response = responseBuilder.build();
        LOG.infof("DummyProcessor returning success: %s", response.getSuccess());

        return Uni.createFrom().item(response);
    }

    @Override
    public Uni<ProcessResponse> testProcessData(ProcessRequest request) {
        return processData(request);
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        LOG.debugf("DummyProcessor registration requested");

        ServiceRegistrationResponse registration = ServiceRegistrationResponse.newBuilder()
                .setModuleName(processorName)
                .setJsonConfigSchema("{\"type\":\"object\",\"properties\":{\"prefix\":{\"type\":\"string\"}}}")
                .build();

        return Uni.createFrom().item(registration);
    }
}
