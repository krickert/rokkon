package com.rokkon.pipeline.engine.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@GrpcService
public class EngineServiceImpl extends EngineServiceGrpc.EngineServiceImplBase {
    
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;
    
    @ConfigProperty(name = "quarkus.application.version")
    String applicationVersion;
    
    @Override
    public void getStatus(Empty request, StreamObserver<EngineStatus> responseObserver) {
        EngineStatus status = EngineStatus.newBuilder()
                .setName(applicationName)
                .setVersion(applicationVersion)
                .setReady(true)
                .build();
        
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }
}