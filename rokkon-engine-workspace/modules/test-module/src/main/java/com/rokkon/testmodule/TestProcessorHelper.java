package com.rokkon.testmodule;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.*;

import java.util.UUID;

/**
 * Helper class for building test requests to the TestProcessor.
 * This makes it easier to test different scenarios with the test module.
 */
public class TestProcessorHelper {
    
    /**
     * Builder for creating test documents with various configurations.
     */
    public static class TestDocumentBuilder {
        private String id = UUID.randomUUID().toString();
        private String title;
        private String body;
        private Struct.Builder customData = Struct.newBuilder();
        
        public TestDocumentBuilder withId(String id) {
            this.id = id;
            return this;
        }
        
        public TestDocumentBuilder withTitle(String title) {
            this.title = title;
            return this;
        }
        
        public TestDocumentBuilder withBody(String body) {
            this.body = body;
            return this;
        }
        
        public TestDocumentBuilder withCustomField(String key, String value) {
            customData.putFields(key, Value.newBuilder().setStringValue(value).build());
            return this;
        }
        
        public TestDocumentBuilder withCustomField(String key, boolean value) {
            customData.putFields(key, Value.newBuilder().setBoolValue(value).build());
            return this;
        }
        
        public TestDocumentBuilder withCustomField(String key, double value) {
            customData.putFields(key, Value.newBuilder().setNumberValue(value).build());
            return this;
        }
        
        public PipeDoc build() {
            PipeDoc.Builder builder = PipeDoc.newBuilder().setId(id);
            
            if (title != null) {
                builder.setTitle(title);
            }
            if (body != null) {
                builder.setBody(body);
            }
            
            Struct customStruct = customData.build();
            if (customStruct.getFieldsCount() > 0) {
                builder.setCustomData(customStruct);
            }
            
            return builder.build();
        }
    }
    
    /**
     * Builder for creating test requests with various configurations.
     */
    public static class TestRequestBuilder {
        private PipeDoc document;
        private String pipelineName = "test-pipeline";
        private String stepName = "test-processor";
        private String streamId = "test-stream-1";
        private long hopNumber = 1;
        private Struct.Builder configBuilder = Struct.newBuilder();
        
        public TestRequestBuilder withDocument(PipeDoc document) {
            this.document = document;
            return this;
        }
        
        public TestRequestBuilder withPipelineName(String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }
        
        public TestRequestBuilder withStepName(String stepName) {
            this.stepName = stepName;
            return this;
        }
        
        public TestRequestBuilder withStreamId(String streamId) {
            this.streamId = streamId;
            return this;
        }
        
        public TestRequestBuilder withHopNumber(long hopNumber) {
            this.hopNumber = hopNumber;
            return this;
        }
        
        public TestRequestBuilder withMode(ProcessingMode mode) {
            configBuilder.putFields("mode", Value.newBuilder().setStringValue(mode.name().toLowerCase()).build());
            return this;
        }
        
        public TestRequestBuilder withSchemaValidation(boolean enabled) {
            configBuilder.putFields("requireSchema", Value.newBuilder().setBoolValue(enabled).build());
            return this;
        }
        
        public TestRequestBuilder withAddMetadata(boolean enabled) {
            configBuilder.putFields("addMetadata", Value.newBuilder().setBoolValue(enabled).build());
            return this;
        }
        
        public TestRequestBuilder withSimulateError(boolean enabled) {
            configBuilder.putFields("simulateError", Value.newBuilder().setBoolValue(enabled).build());
            return this;
        }
        
        public ProcessRequest build() {
            ProcessRequest.Builder builder = ProcessRequest.newBuilder();
            
            if (document != null) {
                builder.setDocument(document);
            }
            
            builder.setMetadata(ServiceMetadata.newBuilder()
                    .setPipelineName(pipelineName)
                    .setPipeStepName(stepName)
                    .setStreamId(streamId)
                    .setCurrentHopNumber(hopNumber)
                    .build());
            
            Struct config = configBuilder.build();
            if (config.getFieldsCount() > 0) {
                builder.setConfig(ProcessConfiguration.newBuilder()
                        .setCustomJsonConfig(config)
                        .build());
            }
            
            return builder.build();
        }
    }
    
    /**
     * Processing modes supported by the test processor.
     */
    public enum ProcessingMode {
        TEST,      // Passthrough mode - minimal processing
        VALIDATE,  // Schema validation mode - validates required fields
        TRANSFORM  // Transform mode - can modify document content
    }
    
    /**
     * Creates a builder for test documents.
     */
    public static TestDocumentBuilder documentBuilder() {
        return new TestDocumentBuilder();
    }
    
    /**
     * Creates a builder for test requests.
     */
    public static TestRequestBuilder requestBuilder() {
        return new TestRequestBuilder();
    }
    
    /**
     * Creates a valid document with all required fields for schema validation.
     */
    public static PipeDoc createValidDocument() {
        return documentBuilder()
                .withTitle("Valid Test Document")
                .withBody("This document contains all required fields for schema validation")
                .withCustomField("source", "test-helper")
                .build();
    }
    
    /**
     * Creates a document missing the title field (invalid for schema validation).
     */
    public static PipeDoc createDocumentWithoutTitle() {
        return documentBuilder()
                .withBody("This document is missing the required title field")
                .withCustomField("source", "test-helper")
                .build();
    }
    
    /**
     * Creates a document missing the body field (invalid for schema validation).
     */
    public static PipeDoc createDocumentWithoutBody() {
        return documentBuilder()
                .withTitle("Document Without Body")
                .withCustomField("source", "test-helper")
                .build();
    }
    
    /**
     * Creates a simple test request with a valid document.
     */
    public static ProcessRequest createSimpleRequest() {
        return requestBuilder()
                .withDocument(createValidDocument())
                .build();
    }
    
    /**
     * Creates a request configured for schema validation mode.
     */
    public static ProcessRequest createSchemaValidationRequest(PipeDoc document) {
        return requestBuilder()
                .withDocument(document)
                .withMode(ProcessingMode.VALIDATE)
                .build();
    }
    
    /**
     * Creates a request configured to simulate an error.
     */
    public static ProcessRequest createErrorRequest() {
        return requestBuilder()
                .withDocument(createValidDocument())
                .withSimulateError(true)
                .build();
    }
}