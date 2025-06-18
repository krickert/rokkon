package com.rokkon.parser;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.protobuf.utils.ProcessingBuffer;
import com.rokkon.search.sdk.*;
import com.rokkon.parser.util.DocumentParser;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

@GrpcService
@Singleton
public class ParserServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(ParserServiceImpl.class);

    @Inject
    ProcessingBuffer<PipeDoc> outputBuffer;

    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.debugf("Parser service received document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");

        return Uni.createFrom().item(() -> {
            try {
                ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                        .setSuccess(true);

                if (request.hasDocument()) {
                    // Extract configuration from request
                    Map<String, String> config = extractConfiguration(request);

                    // Check if document has blob data to parse
                    if (request.getDocument().hasBlob() && request.getDocument().getBlob().getData().size() > 0) {
                        // Get filename from document blob metadata if available
                        String filename = null;
                        if (request.getDocument().getBlob().hasFilename()) {
                            filename = request.getDocument().getBlob().getFilename();
                        }

                        LOG.debugf("Processing document with filename: %s, config keys: %s", 
                                 filename, config.keySet());

                        // Parse the document using Tika
                        PipeDoc parsedDoc = DocumentParser.parseDocument(
                            request.getDocument().getBlob().getData(),
                            config,
                            filename
                        );

                        // Create the output document with the original ID preserved
                        PipeDoc outputDoc = parsedDoc.toBuilder()
                                .setId(request.getDocument().getId())
                                .build();

                        // Add the document to the processing buffer for test data generation
                        outputBuffer.add(outputDoc);
                        LOG.debugf("Added document to processing buffer: %s", outputDoc.getId());

                        responseBuilder.setOutputDoc(outputDoc)
                                .addProcessorLogs("Parser service successfully processed document using Tika")
                                .addProcessorLogs(String.format("Extracted title: '%s'", 
                                        outputDoc.getTitle().isEmpty() ? "none" : outputDoc.getTitle()))
                                .addProcessorLogs(String.format("Extracted body length: %d characters", 
                                        outputDoc.getBody().length()))
                                .addProcessorLogs(String.format("Extracted custom data fields: %d", 
                                        outputDoc.hasCustomData() ? outputDoc.getCustomData().getFieldsCount() : 0));

                        LOG.debugf("Successfully parsed document - title: '%s', body length: %d, custom data fields: %d",
                                 outputDoc.getTitle(), outputDoc.getBody().length(), 
                                 outputDoc.hasCustomData() ? outputDoc.getCustomData().getFieldsCount() : 0);

                    } else {
                        // Document has no blob data to parse - just pass it through
                        responseBuilder.setOutputDoc(request.getDocument())
                                .addProcessorLogs("Parser service received document with no blob data - passing through unchanged");
                        LOG.debug("Document has no blob data to parse - passing through");
                    }

                } else {
                    responseBuilder.addProcessorLogs("Parser service received request with no document");
                    LOG.debug("No document in request to parse");
                }

                return responseBuilder.build();

            } catch (Exception e) {
                LOG.error("Error parsing document: " + e.getMessage(), e);

                return ProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Parser service failed to process document: " + e.getMessage())
                        .addProcessorLogs("Error type: " + e.getClass().getSimpleName())
                        .build();
            }
        });
    }

    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        LOG.debug("Parser service registration requested");

        return Uni.createFrom().item(() -> {
            try {
                // Load the JSON schema from resources
                String schema = loadSchemaFromResources();

                ServiceRegistrationData response = ServiceRegistrationData.newBuilder()
                        .setModuleName("parser-module")
                        .setJsonConfigSchema(schema)
                        .build();

                LOG.debug("Returning parser service registration with schema");
                return response;

            } catch (Exception e) {
                LOG.error("Failed to load parser schema: " + e.getMessage(), e);

                // Return registration without schema if loading fails
                ServiceRegistrationData response = ServiceRegistrationData.newBuilder()
                        .setModuleName("parser-module")
                        .build();

                return response;
            }
        });
    }

    /**
     * Loads the parser configuration schema from resources.
     */
    private String loadSchemaFromResources() throws Exception {
        try (var inputStream = getClass().getResourceAsStream("/schemas/parser-config-schema.json")) {
            if (inputStream == null) {
                throw new IllegalStateException("Parser schema file not found: /schemas/parser-config-schema.json");
            }

            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Extracts configuration parameters from the process request.
     */
    private Map<String, String> extractConfiguration(ProcessRequest request) {
        Map<String, String> config = new HashMap<>();

        // Extract from config params if available
        if (request.hasConfig()) {
            config.putAll(request.getConfig().getConfigParamsMap());

            // Extract from custom JSON config if available
            if (request.getConfig().hasCustomJsonConfig()) {
                Struct jsonConfig = request.getConfig().getCustomJsonConfig();
                extractConfigFromStruct(jsonConfig, config, "");
            }
        }

        return config;
    }

    /**
     * Recursively extracts configuration from Protobuf Struct.
     */
    private void extractConfigFromStruct(Struct struct, Map<String, String> config, String prefix) {
        for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Value value = entry.getValue();

            switch (value.getKindCase()) {
                case STRING_VALUE:
                    config.put(key, value.getStringValue());
                    break;
                case NUMBER_VALUE:
                    config.put(key, String.valueOf(value.getNumberValue()));
                    break;
                case BOOL_VALUE:
                    config.put(key, String.valueOf(value.getBoolValue()));
                    break;
                case STRUCT_VALUE:
                    extractConfigFromStruct(value.getStructValue(), config, key);
                    break;
                default:
                    // Skip other value types for now
                    break;
            }
        }
    }
}
