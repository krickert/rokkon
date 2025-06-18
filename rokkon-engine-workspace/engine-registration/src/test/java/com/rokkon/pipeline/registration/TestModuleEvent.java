package com.rokkon.pipeline.registration;

import java.time.Instant;

/**
 * Event fired by test module during processing
 */
public record TestModuleEvent(
    String eventType,
    String documentId,
    Instant timestamp
) {
    public TestModuleEvent(String eventType, String documentId) {
        this(eventType, documentId, Instant.now());
    }
}