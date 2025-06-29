package com.rokkon.pipeline.config.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.Map;

/**
 * gRPC transport configuration for pipeline step outputs.
 * 
 * <h3>Design Decisions:</h3>
 * <ul>
 *   <li><b>Service Discovery</b>: 100% through Consul - this is why we use Consul!</li>
 *   <li><b>Host/Port Resolution</b>: Dynamically resolved via Consul service registry</li>
 *   <li><b>TLS Settings</b>: Configured at global/instance level, not per-transport</li>
 *   <li><b>Load Balancing</b>: Handled by Consul (round-robin or least-connections)</li>
 * </ul>
 * 
 * <h3>Configuration Flow:</h3>
 * <ol>
 *   <li>Service name is looked up in Consul</li>
 *   <li>Consul returns healthy instances with host:port</li>
 *   <li>gRPC client connects using Consul's selection</li>
 *   <li>Health checks maintain service registry accuracy</li>
 * </ol>
 * 
 * <h3>Properties Map:</h3>
 * Common properties include:
 * <ul>
 *   <li>{@code timeout} - Call timeout in milliseconds</li>
 *   <li>{@code retry} - Number of retry attempts</li>
 *   <li>{@code loadBalancingPolicy} - Override Consul's default</li>
 * </ul>
 * 
 * @see PipelineStepConfig.OutputTarget
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "gRPC transport configuration")
public record GrpcTransportConfig(
        @JsonProperty("serviceName") String serviceName, // Consul service name of the TARGET gRPC service
        @JsonProperty("grpcClientProperties") Map<String, String> grpcClientProperties
        // e.g., timeout, loadBalancingPolicy for THIS output call
) {
    @JsonCreator
    public GrpcTransportConfig(
            @JsonProperty("serviceName") String serviceName,
            @JsonProperty("grpcClientProperties") Map<String, String> grpcClientProperties
    ) {
        this.serviceName = serviceName; // Can be null if not a GRPC output, validation by OutputTarget
        this.grpcClientProperties = (grpcClientProperties == null) ? Collections.emptyMap() : Map.copyOf(grpcClientProperties);
    }
}