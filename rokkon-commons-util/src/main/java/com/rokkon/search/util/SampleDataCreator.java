package com.rokkon.search.util;

import com.google.protobuf.Timestamp;
import java.time.Instant;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessResponse;

/**
 * Creates sample data for testing purposes.
 */
public class SampleDataCreator {

    /**
     * Creates a sample PipeDoc with test content.
     */
    public static PipeDoc createSamplePipeDoc(String id, String title, String body) {
        Instant now = Instant.now();

        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle(title)
                .setBody(body)
                .setDocumentType("sample")
                .setSourceUri("sample://test-document/" + id)
                .setSourceMimeType("text/plain")
                .setCreationDate(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .setProcessedDate(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .putMetadata("source", "SampleDataCreator")
                .putMetadata("version", "1.0")
                .addKeywords("sample")
                .addKeywords("test")
                .addKeywords("echo")
                .build();
    }

    /**
     * Creates a default sample PipeDoc.
     */
    public static PipeDoc createDefaultSamplePipeDoc() {
        return createSamplePipeDoc(
                "sample-001",
                "Sample Document for Echo Testing",
                "This is a sample document created for testing the Echo module. " +
                "It contains multiple paragraphs to demonstrate how the echo service processes text content.\n\n" +
                "The Echo module is designed to receive a document and return it unchanged. " +
                "This allows us to test the gRPC communication and ensure that documents " +
                "are properly serialized and deserialized through the pipeline.\n\n" +
                "This document includes:\n" +
                "- A meaningful title\n" +
                "- Body content with multiple paragraphs\n" +
                "- Metadata fields\n" +
                "- Keywords for searchability\n\n" +
                "The echo service should return this exact content without any modifications."
        );
    }

    /**
     * Creates a sample PipeStream containing a document.
     */
    public static PipeStream createSamplePipeStream(String streamId, PipeDoc document) {
        return PipeStream.newBuilder()
                .setStreamId(streamId)
                .setDocument(document)
                .setCurrentPipelineName("test-pipeline")
                .setTargetStepName("echo")
                .setCurrentHopNumber(1)
                .putContextParams("test", "true")
                .putContextParams("source", "SampleDataCreator")
                .build();
    }

    /**
     * Creates a sample ProcessResponse containing a document.
     */
    public static ProcessResponse createSampleProcessResponse(PipeDoc document, boolean success) {
        ProcessResponse.Builder builder = ProcessResponse.newBuilder()
                .setSuccess(success);

        if (document != null) {
            builder.setOutputDoc(document);
        }

        if (success) {
            builder.addProcessorLogs("Sample document processed successfully");
        } else {
            builder.addProcessorLogs("Sample processing failed");
        }

        return builder.build();
    }
}
