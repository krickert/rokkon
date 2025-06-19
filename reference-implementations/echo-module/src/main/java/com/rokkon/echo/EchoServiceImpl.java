package com.rokkon.echo;

import com.rokkon.echo.grpc.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

@GrpcService
@Singleton
public class EchoServiceImpl implements EchoService {

    @Override
    public Uni<EchoResponse> echo(EchoRequest request) {
        return Uni.createFrom().item(() -> 
            EchoResponse.newBuilder()
                .setMessage("Echo: " + request.getMessage())
                .setTimestamp(Instant.now().toEpochMilli())
                .build()
        );
    }

    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // Echo module just returns the input as-is
        return Uni.createFrom().item(() ->
            ProcessResponse.newBuilder()
                .setId(request.getId())
                .setProcessedContent(request.getContent())
                .putAllMetadata(request.getMetadataMap())
                .setStatus("SUCCESS")
                .build()
        );
    }

    @Override
    public Uni<ServiceRegistrationData> getServiceRegistration(Empty request) {
        return Uni.createFrom().item(() ->
            ServiceRegistrationData.newBuilder()
                .setModuleName("echo")
                .setModuleVersion("1.0.0")
                .addAllSupportedInputTypes(List.of("*/*"))  // Accepts any input
                .addAllSupportedOutputTypes(List.of("*/*")) // Returns same as input
                .build()
        );
    }
}