package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reference to a schema in the registry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Reference to a schema in the registry")
public record SchemaReference(
        @JsonProperty("subject") String subject,
        @JsonProperty("version") Integer version
) {
    public SchemaReference {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("SchemaReference subject cannot be null or blank.");
        }
        if (version == null || version < 1) {
            throw new IllegalArgumentException("SchemaReference version cannot be null and must be positive.");
        }
    }

    /**
     * Returns a string representation combining subject and version,
     * suitable for logging or as a unique identifier.
     * Example: "my-schema-subject:3"
     *
     * @return A string combining subject and version.
     */
    public String toIdentifier() {
        return String.format("%s:%s", subject, version);
    }
}