package com.rokkon.echo;

import com.rokkon.echo.grpc.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class EchoServiceTestBase {

    protected abstract EchoService getEchoService();

    @Test
    void testEcho() {
        EchoRequest request = EchoRequest.newBuilder()
                .setMessage("Hello Quarkus")
                .build();

        var response = getEchoService().echo(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getMessage()).isEqualTo("Echo: Hello Quarkus");
        assertThat(response.getTimestamp()).isGreaterThan(0);
    }

    @Test
    void testProcessData() {
        ProcessRequest request = ProcessRequest.newBuilder()
                .setId("test-123")
                .setContent(com.google.protobuf.ByteString.copyFromUtf8("Test content"))
                .putMetadata("key", "value")
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(response.getId()).isEqualTo("test-123");
        assertThat(response.getProcessedContent()).isEqualTo(request.getContent());
        assertThat(response.getMetadataMap()).containsEntry("key", "value");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void testGetServiceRegistration() {
        var registration = getEchoService().getServiceRegistration(Empty.newBuilder().build())
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat(registration.getModuleName()).isEqualTo("echo");
        assertThat(registration.getModuleVersion()).isEqualTo("1.0.0");
        assertThat(registration.getSupportedInputTypesList()).contains("*/*");
        assertThat(registration.getSupportedOutputTypesList()).contains("*/*");
    }
}