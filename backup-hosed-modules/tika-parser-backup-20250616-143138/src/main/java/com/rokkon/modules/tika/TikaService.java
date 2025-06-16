package com.rokkon.modules.tika;

import com.rokkon.search.model.Blob;
import com.rokkon.search.model.ParsedDocument;
import com.rokkon.search.model.ParsedDocumentReply;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ServiceRegistrationData;
// 1. IMPORT THE MUTINY INTERFACE
import com.rokkon.search.sdk.MutinyPipeStepProcessorGrpc;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.util.Map;

@GrpcService
public class TikaService extends MutinyPipeStepProcessorGrpc.PipeStepProcessorImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaService.class);

    @Inject
    TikaConfiguration tikaConfiguration;

    // --- NO OTHER CHANGES ARE NEEDED BELOW THIS LINE ---
    // Your existing method implementations are perfect for the Mutiny interface.

    @Override
    @RunOnVirtualThread
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                if (request == null) {
                    LOG.error("Received null request");
                    return createErrorResponse("Request cannot be null", "NULL_REQUEST", null);
                }

                LOG.info("Processing document with Tika parser");

                PipeDoc pipeDoc = request.getDocument();
                Map<String, String> config = request.getConfig().getConfigParamsMap();
                Map<String, String> mergedConfig = tikaConfiguration.mergeWithDefaults(config);

                ParsedDocumentReply reply = DocumentParser.parseDocument(
                        pipeDoc.getBlob().getData(),
                        mergedConfig
                );

                PipeDoc.Builder docBuilder = pipeDoc.toBuilder();
                ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                        .setOutputDoc(docBuilder.build())
                        .setSuccess(reply.getSuccess());

                if (reply.getSuccess()) {
                    ParsedDocument parsedDoc = reply.getParsedDocument();
                    docBuilder.setTitle(parsedDoc.getTitle());
                    docBuilder.setBody(parsedDoc.getBody());

                    if (!parsedDoc.getMetadataMap().isEmpty()) {
                        Blob.Builder blobBuilder = docBuilder.getBlob().toBuilder();
                        blobBuilder.putAllMetadata(parsedDoc.getMetadataMap());
                        docBuilder.setBlob(blobBuilder.build());
                    }

                    responseBuilder.setOutputDoc(docBuilder.build());
                    responseBuilder.addProcessorLogs("Successfully parsed document: " + parsedDoc.getTitle());
                    LOG.info("Successfully parsed document: {}", parsedDoc.getTitle());
                } else {
                    LOG.error("Failed to parse document: {}", reply.getErrorMessage());

                    Struct errorDetails = Struct.newBuilder()
                            .putFields("error_message", Value.newBuilder().setStringValue(reply.getErrorMessage()).build())
                            .putFields("error_code", Value.newBuilder().setStringValue("PARSING_FAILED").build())
                            .build();

                    responseBuilder.setErrorDetails(errorDetails)
                            .addProcessorLogs("ERROR: " + reply.getErrorMessage());
                }

                return responseBuilder.build();

            } catch (Exception e) {
                LOG.error("Error processing document in Tika service", e);
                return createErrorResponse(
                        "Error processing document: " + e.getMessage(),
                        "PROCESSING_EXCEPTION",
                        request != null && request.hasDocument() ? request.getDocument() : null
                );
            }
        });
    }

    @Override
    @RunOnVirtualThread
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        return Uni.createFrom().item(() -> {
            try {
                String schema;
                try (var inputStream = getClass().getClassLoader()
                        .getResourceAsStream("tika-config-schema.json")) {
                    if (inputStream != null) {
                        schema = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        LOG.warn("Could not find tika-config-schema.json in resources");
                        schema = null;
                    }
                }

                ServiceRegistrationData.Builder registrationBuilder = ServiceRegistrationData.newBuilder()
                        .setModuleName("tika-parser");

                if (schema != null) {
                    registrationBuilder.setJsonConfigSchema(schema);
                    LOG.info("Registered Tika parser with JSON schema validation");
                } else {
                    LOG.info("Registered Tika parser without schema validation");
                }

                return registrationBuilder.build();

            } catch (Exception e) {
                LOG.error("Error getting service registration", e);
                return ServiceRegistrationData.newBuilder()
                        .setModuleName("tika-parser")
                        .build();
            }
        });
    }

    private ProcessResponse createErrorResponse(String errorMessage, String errorCode, PipeDoc originalDoc) {
        Struct errorDetails = Struct.newBuilder()
                .putFields("error_message", Value.newBuilder().setStringValue(errorMessage).build())
                .putFields("error_code", Value.newBuilder().setStringValue(errorCode).build())
                .build();

        ProcessResponse.Builder errorBuilder = ProcessResponse.newBuilder()
                .setSuccess(false)
                .setErrorDetails(errorDetails)
                .addProcessorLogs("ERROR: " + errorMessage);

        if (originalDoc != null) {
            errorBuilder.setOutputDoc(originalDoc);
        }

        return errorBuilder.build();
    }
}