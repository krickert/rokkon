package com.rokkon.modules.tika;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Collections;
import java.util.Map;

public class GrpcPortResource implements QuarkusTestResourceLifecycleManager {

    private Integer grpcPort;

    @Override
    public Map<String, String> start() {
        // This method runs BEFORE the Quarkus application starts.
        // We don't need to do anything here.
        return Collections.emptyMap();
    }

    /**
     * This is the key method. It runs AFTER the Quarkus application has started and its
     * configuration, including the random gRPC port, is available.
     */
    @Override
    public void inject(TestInjector testInjector) {
        // 1. Get the configuration of the now-running Quarkus application.
        Config config = ConfigProvider.getConfig();

        // 2. Look up the value of the gRPC server port.
        // In integration tests, the property is 'quarkus.grpc.server.port'.
        this.grpcPort = config.getValue("quarkus.grpc.server.port", Integer.class);

        // 3. Use the TestInjector to inject our discovered port value into any field
        //    in the test class that is annotated with our custom @InjectGrpcPort annotation.
        testInjector.injectIntoFields(
            this.grpcPort,
            new TestInjector.AnnotatedAndMatchesType(InjectGrpcPort.class, Integer.class)
        );
    }

    @Override
    public void stop() {
        // Cleanup if needed
    }
}