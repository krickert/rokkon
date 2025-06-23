package com.rokkon.protobuf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify that the protobuf JAR packages all required proto files correctly.
 */
class ProtoJarPackagingTest {

    private static final List<String> EXPECTED_PROTO_FILES = Arrays.asList(
        "connector_service.proto",
        "engine_service.proto",
        "module_registration.proto",
        "pipe_step_processor_service.proto",
        "rokkon_core_types.proto",
        "rokkon_service_registration.proto",
        "test_harness.proto",
        "tika_parser.proto"
    );

    @Test
    void testProtoFilesAreAccessible() {
        // Verify each proto file can be loaded as a resource
        for (String protoFile : EXPECTED_PROTO_FILES) {
            URL resource = getClass().getClassLoader().getResource(protoFile);
            assertThat(resource)
                .as("Proto file %s should be accessible as a resource", protoFile)
                .isNotNull();
        }
    }

    @Test
    void testProtoFilesContent() throws IOException {
        // Verify we can read the content of a proto file
        String testProto = "rokkon_core_types.proto";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(testProto)) {
            assertThat(is)
                .as("Should be able to open %s as stream", testProto)
                .isNotNull();
            
            byte[] content = is.readAllBytes();
            assertThat(content.length)
                .as("Proto file %s should have content", testProto)
                .isGreaterThan(0);
            
            String contentStr = new String(content);
            assertThat(contentStr)
                .as("Proto file should contain proto syntax declaration")
                .contains("syntax = \"proto3\"");
        }
    }

    @Test
    void testProtoSyntaxIsValid() {
        // Verify all proto files have valid proto3 syntax
        for (String protoFile : EXPECTED_PROTO_FILES) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(protoFile)) {
                assertThat(is)
                    .as("Should be able to open %s", protoFile)
                    .isNotNull();
                
                String content = new String(is.readAllBytes());
                assertThat(content)
                    .as("Proto file %s should declare proto3 syntax", protoFile)
                    .contains("syntax = \"proto3\"");
                
                // Basic validation - should have package declaration
                assertThat(content)
                    .as("Proto file %s should have package declaration", protoFile)
                    .matches("(?s).*package\\s+com\\.rokkon\\..*");
            } catch (IOException e) {
                throw new AssertionError("Failed to read proto file: " + protoFile, e);
            }
        }
    }

    @Test
    void testJarContainsProtoFiles() throws IOException {
        // This test verifies the JAR structure when run as part of build
        // It will be skipped if not running in a build context
        String jarPath = System.getProperty("project.build.directory");
        if (jarPath == null) {
            // Not running in build context, use classloader to verify
            testProtoFilesAreAccessible();
            return;
        }

        // If we have access to the built JAR, verify its contents
        URL jarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        if (jarUrl != null && jarUrl.getPath().endsWith(".jar")) {
            verifyJarContents(jarUrl);
        }
    }

    private void verifyJarContents(URL jarUrl) throws IOException {
        try (JarInputStream jarStream = new JarInputStream(jarUrl.openStream())) {
            boolean foundProto = false;
            boolean foundGeneratedClass = false;
            
            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".proto")) {
                    foundProto = true;
                }
                if (name.endsWith(".class") && name.contains("com/rokkon/search")) {
                    foundGeneratedClass = true;
                }
            }
            
            assertThat(foundProto)
                .as("JAR should contain proto files")
                .isTrue();
            assertThat(foundGeneratedClass)
                .as("JAR should contain generated classes")
                .isTrue();
        }
    }
}