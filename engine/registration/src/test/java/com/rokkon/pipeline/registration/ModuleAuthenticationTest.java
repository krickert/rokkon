package com.rokkon.pipeline.registration;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.rokkon.search.grpc.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for module authentication feature in registration service.
 * Verifies:
 * 1. New modules can register successfully when whitelisted
 * 2. Existing modules with the same schema can re-register (idempotent)
 * 3. Existing modules with different schemas are rejected
 * 4. Non-whitelisted modules are rejected
 */
@QuarkusTest
class ModuleAuthenticationTest {

    @GrpcClient
    ModuleRegistration registrationService;

    @InjectMock
    ConsulModuleRegistry consulRegistry;

    private ModuleInfo.Builder createTestModuleInfo(String moduleName) {
        return ModuleInfo.newBuilder()
                .setServiceName(moduleName)
                .setServiceId(moduleName + "-123")
                .setHost("localhost")
                .setPort(9090)
                .setHealthEndpoint("/health");
    }

    private ModuleInfo.Builder createTestModuleInfoWithSchema(String moduleName, String schema) {
        ModuleInfo.Builder builder = createTestModuleInfo(moduleName);
        if (schema != null && !schema.isEmpty()) {
            // Note: Since schema_definition field doesn't exist in proto, 
            // we're using metadata to store schema for now
            builder.putMetadata("schema", schema);
        }
        return builder;
    }

    private Timestamp getCurrentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        Mockito.reset(consulRegistry);
    }

    @Test
    void testNewWhitelistedModuleCanRegisterSuccessfully() {
        // Given: A new whitelisted module (echo) that is not already registered
        ModuleInfo moduleInfo = createTestModuleInfo("echo").build();
        String expectedConsulServiceId = "grpc-echo-123";
        
        when(consulRegistry.getExistingModule("echo"))
                .thenReturn(Uni.createFrom().item(Optional.empty()));
        when(consulRegistry.registerService(any(ModuleInfo.class)))
                .thenReturn(Uni.createFrom().item(expectedConsulServiceId));

        // When: The module attempts to register
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Registration should succeed
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successfully registered");
        assertThat(result.getConsulServiceId()).isEqualTo(expectedConsulServiceId);
        assertThat(result.hasRegisteredAt()).isTrue();

        // Verify Consul was called
        verify(consulRegistry).getExistingModule("echo");
        verify(consulRegistry).registerService(moduleInfo);
    }

    @Test
    void testExistingModuleWithSameSchemaCanReRegister() {
        // Given: An existing module with the same schema re-registering
        String schema = "{\"type\":\"object\",\"properties\":{\"mode\":{\"type\":\"string\"}}}";
        
        ModuleInfo moduleInfo = createTestModuleInfoWithSchema("chunker", schema).build();
        ModuleInfo existingModule = createTestModuleInfoWithSchema("chunker", schema).build();
        
        String expectedConsulServiceId = "grpc-chunker-123";
        
        when(consulRegistry.getExistingModule("chunker"))
                .thenReturn(Uni.createFrom().item(Optional.of(existingModule)));
        when(consulRegistry.registerService(any(ModuleInfo.class)))
                .thenReturn(Uni.createFrom().item(expectedConsulServiceId));

        // When: The module attempts to re-register
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Registration should succeed (idempotent)
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successfully registered");
        assertThat(result.getConsulServiceId()).isEqualTo(expectedConsulServiceId);

        // Verify Consul was called
        verify(consulRegistry).getExistingModule("chunker");
        verify(consulRegistry).registerService(moduleInfo);
    }

    @Test
    void testExistingModuleWithDifferentSchemaIsRejected() {
        // Given: An existing module trying to register with a different schema
        String existingSchema = "{\"type\":\"object\",\"properties\":{\"mode\":{\"type\":\"string\"}}}";
        String newSchema = "{\"type\":\"object\",\"properties\":{\"mode\":{\"type\":\"string\"},\"debug\":{\"type\":\"boolean\"}}}";
        
        ModuleInfo moduleInfo = createTestModuleInfoWithSchema("parser", newSchema).build();
        ModuleInfo existingModule = createTestModuleInfoWithSchema("parser", existingSchema).build();
        
        when(consulRegistry.getExistingModule("parser"))
                .thenReturn(Uni.createFrom().item(Optional.of(existingModule)));

        // When: The module attempts to register with different schema
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Registration should fail due to schema mismatch
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Authentication failed");
        assertThat(result.getMessage()).contains("Schema mismatch");
        assertThat(result.getMessage()).contains("coordinate with pipeline owners");
        assertThat(result.getConsulServiceId()).isEmpty();

        // Verify Consul registry was checked but not called for registration
        verify(consulRegistry).getExistingModule("parser");
        verify(consulRegistry, never()).registerService(any());
        
    }

    @Test
    void testNonWhitelistedModuleIsRejected() {
        // Given: A non-whitelisted module attempting to register
        ModuleInfo moduleInfo = createTestModuleInfo("malicious-module").build();

        // When: The non-whitelisted module attempts to register
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Registration should fail due to not being whitelisted
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Authentication failed");
        assertThat(result.getMessage()).contains("not allowed");
        assertThat(result.getMessage()).contains("contact administrators");
        assertThat(result.getConsulServiceId()).isEmpty();

        // Verify Consul was never called
        verify(consulRegistry, never()).getExistingModule(any());
        verify(consulRegistry, never()).registerService(any());
        
    }

    @Test
    void testAllWhitelistedModulesCanAuthenticate() {
        // Test all whitelisted modules can authenticate
        String[] whitelistedModules = {"echo", "chunker", "parser", "embedder", "opensearch-sink", "test-module"};
        
        for (String moduleName : whitelistedModules) {
            // Reset mocks for each iteration
            Mockito.reset(consulRegistry);
            
            ModuleInfo moduleInfo = createTestModuleInfo(moduleName).build();
            
            when(consulRegistry.getExistingModule(moduleName))
                    .thenReturn(Uni.createFrom().item(Optional.empty()));
            when(consulRegistry.registerService(any(ModuleInfo.class)))
                    .thenReturn(Uni.createFrom().item("grpc-" + moduleName + "-123"));

            RegistrationStatus result = registrationService.registerModule(moduleInfo)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();

            assertThat(result.getSuccess())
                    .as("Module %s should be whitelisted and register successfully", moduleName)
                    .isTrue();
        }
    }

    @Test
    void testModulesWithBothEmptySchemasCanReRegister() {
        // Given: Both existing and new module have empty schemas
        ModuleInfo moduleInfo = createTestModuleInfo("echo").build();
        ModuleInfo existingModule = createTestModuleInfo("echo").build();
        
        String expectedConsulServiceId = "grpc-echo-123";
        
        when(consulRegistry.getExistingModule("echo"))
                .thenReturn(Uni.createFrom().item(Optional.of(existingModule)));
        when(consulRegistry.registerService(any(ModuleInfo.class)))
                .thenReturn(Uni.createFrom().item(expectedConsulServiceId));

        // When: The module attempts to re-register
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Registration should succeed
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successfully registered");
    }

    @Test
    void testConsulRegistrationFailureReturnsError() {
        // Given: A whitelisted module but Consul registration fails
        ModuleInfo moduleInfo = createTestModuleInfo("embedder").build();
        
        when(consulRegistry.getExistingModule("embedder"))
                .thenReturn(Uni.createFrom().item(Optional.empty()));
        when(consulRegistry.registerService(any(ModuleInfo.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Consul connection failed")));

        // When: The module attempts to register
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Registration should fail with error message
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Registration failed");
        assertThat(result.getMessage()).contains("Consul connection failed");

    }

    @Test
    void testConsulCheckFailureReturnsError() {
        // Given: Consul fails when checking existing module
        ModuleInfo moduleInfo = createTestModuleInfo("opensearch-sink").build();
        
        when(consulRegistry.getExistingModule("opensearch-sink"))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Consul unavailable")));

        // When: The module attempts to register
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Registration should fail
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Authentication failed");
        assertThat(result.getMessage()).contains("Failed to verify module");

        // Verify no registration was attempted
        verify(consulRegistry, never()).registerService(any());
    }

    @Test
    void testRegistrationWithDifferentModuleInstanceIds() {
        // Given: Two instances of the same module type with different IDs
        ModuleInfo firstInstance = ModuleInfo.newBuilder()
                .setServiceName("echo")
                .setServiceId("echo-instance-1")
                .setHost("host1")
                .setPort(9090)
                .build();
                
        ModuleInfo secondInstance = ModuleInfo.newBuilder()
                .setServiceName("echo")
                .setServiceId("echo-instance-2")
                .setHost("host2")
                .setPort(9091)
                .build();
        
        when(consulRegistry.getExistingModule("echo"))
                .thenReturn(Uni.createFrom().item(Optional.empty()))
                .thenReturn(Uni.createFrom().item(Optional.of(firstInstance)));
        when(consulRegistry.registerService(any(ModuleInfo.class)))
                .thenReturn(Uni.createFrom().item("grpc-echo-instance-1"))
                .thenReturn(Uni.createFrom().item("grpc-echo-instance-2"));

        // When: Both instances register
        RegistrationStatus result1 = registrationService.registerModule(firstInstance)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
                
        RegistrationStatus result2 = registrationService.registerModule(secondInstance)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Both should succeed
        assertThat(result1.getSuccess()).isTrue();
        assertThat(result2.getSuccess()).isTrue();
        
        // Verify both were registered
        verify(consulRegistry, times(2)).registerService(any(ModuleInfo.class));
    }

    // Additional edge case tests
    
    @Test
    void testRegistrationWithNullMetadata() {
        // Given: A module with no metadata
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName("echo")
                .setServiceId("echo-no-metadata")
                .setHost("localhost")
                .setPort(9090)
                .build();
                
        when(consulRegistry.getExistingModule("echo"))
                .thenReturn(Uni.createFrom().item(Optional.empty()));
        when(consulRegistry.registerService(any(ModuleInfo.class)))
                .thenReturn(Uni.createFrom().item("grpc-echo-no-metadata"));

        // When: The module registers
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Should succeed
        assertThat(result.getSuccess()).isTrue();
    }

    @Test
    void testAuthenticationCheckWithEmptyModuleName() {
        // Given: A module with empty name
        ModuleInfo moduleInfo = ModuleInfo.newBuilder()
                .setServiceName("")
                .setServiceId("empty-name-123")
                .setHost("localhost")
                .setPort(9090)
                .build();

        // When: The module attempts to register
        RegistrationStatus result = registrationService.registerModule(moduleInfo)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Then: Should fail authentication
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Authentication failed");
        assertThat(result.getMessage()).contains("not allowed");
    }
}