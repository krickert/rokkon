package com.rokkon.pipeline.chunker;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class ChunkerTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Configure the gRPC client to connect to the test server
            "quarkus.grpc.clients.chunker.host", "localhost",
            "quarkus.grpc.clients.chunker.port", "49092"  // Same as server port
        );
    }
}