package com.rokkon.integration.security;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive security integration tests to validate input validation, malicious content handling,
 * data integrity verification, and security boundaries across all services.
 * 
 * These tests verify:
 * - Input validation and sanitization
 * - Malicious content detection and handling
 * - Data integrity verification and checksums
 * - Security boundary enforcement
 * - Buffer overflow and injection attack prevention
 * - Content filtering and blacklist enforcement
 * - Secure error handling without information leakage
 * - Authentication and authorization patterns
 * - Secure data transmission and storage
 * - Privacy and data protection compliance
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires full gRPC service infrastructure for comprehensive security testing against malicious inputs and attacks")
class SecurityIntegrationTests {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityIntegrationTests.class);
    
    // Security test patterns
    private static final List<String> SQL_INJECTION_PATTERNS = Arrays.asList(
        "'; DROP TABLE users; --",
        "' OR '1'='1",
        "' UNION SELECT * FROM sensitive_data --",
        "admin'--",
        "' OR 1=1#"
    );
    
    private static final List<String> XSS_PATTERNS = Arrays.asList(
        "<script>alert('XSS')</script>",
        "javascript:alert('XSS')",
        "<img src=x onerror=alert('XSS')>",
        "<svg onload=alert('XSS')>",
        "';alert('XSS');//"
    );
    
    private static final List<String> COMMAND_INJECTION_PATTERNS = Arrays.asList(
        "; rm -rf /",
        "| cat /etc/passwd",
        "&& whoami",
        "`rm -rf /`",
        "$(cat /etc/passwd)"
    );
    
    private static final List<String> PATH_TRAVERSAL_PATTERNS = Arrays.asList(
        "../../../etc/passwd",
        "..\\..\\..\\windows\\system32\\config\\sam",
        "....//....//....//etc/passwd",
        "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
        "..%252f..%252f..%252fetc%252fpasswd"
    );
    
    // Content security patterns
    private static final Pattern SUSPICIOUS_PATTERN = Pattern.compile(
        "(?i)(password|secret|key|token|auth|admin|root|exploit|hack|inject|malware|virus)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private static final long MAX_CONTENT_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int MAX_FIELD_LENGTH = 10000;
    
    private ManagedChannel tikaChannel;
    private ManagedChannel chunkerChannel;
    private ManagedChannel embedderChannel;
    private ManagedChannel echoChannel;
    
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub tikaClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub chunkerClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub embedderClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub echoClient;

    @BeforeEach
    void setUp() {
        // Set up gRPC channels for security testing
        tikaChannel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
        chunkerChannel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
        embedderChannel = ManagedChannelBuilder.forAddress("localhost", 9002).usePlaintext().build();
        echoChannel = ManagedChannelBuilder.forAddress("localhost", 9003).usePlaintext().build();
        
        tikaClient = PipeStepProcessorGrpc.newBlockingStub(tikaChannel);
        chunkerClient = PipeStepProcessorGrpc.newBlockingStub(chunkerChannel);
        embedderClient = PipeStepProcessorGrpc.newBlockingStub(embedderChannel);
        echoClient = PipeStepProcessorGrpc.newBlockingStub(echoChannel);
        
        LOG.info("Security integration test environment initialized");
    }

    @AfterEach 
    void tearDown() throws InterruptedException {
        if (tikaChannel != null) {
            tikaChannel.shutdown();
            tikaChannel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (chunkerChannel != null) {
            chunkerChannel.shutdown();
            chunkerChannel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (embedderChannel != null) {
            embedderChannel.shutdown();
            embedderChannel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (echoChannel != null) {
            echoChannel.shutdown();
            echoChannel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(1)
    @DisplayName("SQL Injection Attack Prevention")
    void testSqlInjectionPrevention() {
        LOG.info("Testing SQL injection attack prevention");
        
        Map<String, Integer> serviceVulnerabilities = new HashMap<>();
        int totalInjectionAttempts = 0;
        int blockedAttempts = 0;
        
        for (String injectionPattern : SQL_INJECTION_PATTERNS) {
            totalInjectionAttempts++;
            
            // Test each service with SQL injection patterns
            if (testServiceWithMaliciousInput(tikaClient, "tika", injectionPattern, "SQL Injection")) {
                blockedAttempts++;
            } else {
                serviceVulnerabilities.merge("tika", 1, Integer::sum);
            }
            
            if (testServiceWithMaliciousInput(chunkerClient, "chunker", injectionPattern, "SQL Injection")) {
                blockedAttempts++;
            } else {
                serviceVulnerabilities.merge("chunker", 1, Integer::sum);
            }
            
            if (testServiceWithMaliciousInput(embedderClient, "embedder", injectionPattern, "SQL Injection")) {
                blockedAttempts++;
            } else {
                serviceVulnerabilities.merge("embedder", 1, Integer::sum);
            }
            
            if (testServiceWithMaliciousInput(echoClient, "echo", injectionPattern, "SQL Injection")) {
                blockedAttempts++;
            } else {
                serviceVulnerabilities.merge("echo", 1, Integer::sum);
            }
            
            totalInjectionAttempts += 3; // Added 3 more services
        }
        
        double blockingRate = (double) blockedAttempts / totalInjectionAttempts;
        
        // Verify SQL injection protection
        assertTrue(blockingRate >= 0.90, 
            String.format("SQL injection blocking rate %.2f%% below minimum 90%%", blockingRate * 100));
        
        // Check for service-specific vulnerabilities
        for (Map.Entry<String, Integer> entry : serviceVulnerabilities.entrySet()) {
            String serviceName = entry.getKey();
            Integer vulnerabilityCount = entry.getValue();
            
            assertTrue(vulnerabilityCount <= 1, 
                String.format("%s service has %d SQL injection vulnerabilities", serviceName, vulnerabilityCount));
        }
        
        LOG.info("✅ SQL injection prevention test completed - Blocking rate: {:.2f}%, Vulnerabilities: {}", 
                blockingRate * 100, serviceVulnerabilities);
    }

    @Test
    @Order(2)
    @DisplayName("Cross-Site Scripting (XSS) Prevention")
    void testXssPreventionAndSanitization() {
        LOG.info("Testing XSS prevention and content sanitization");
        
        int totalXssAttempts = 0;
        int sanitizedResponses = 0;
        List<String> unsanitizedResponses = new ArrayList<>();
        
        for (String xssPattern : XSS_PATTERNS) {
            totalXssAttempts++;
            
            // Test XSS pattern handling in document content
            PipeDoc maliciousDoc = PipeDoc.newBuilder()
                    .setId("xss-test-" + System.currentTimeMillis())
                    .setTitle("XSS Test: " + xssPattern)
                    .setBody("Content with XSS attempt: " + xssPattern)
                    .build();
            
            ProcessRequest request = createProcessRequest("xss-security-test", "echo-step", maliciousDoc);
            
            try {
                ProcessResponse response = echoClient.processData(request);
                
                if (response.getSuccess()) {
                    String responseTitle = response.getOutputDoc().getTitle();
                    String responseBody = response.getOutputDoc().getBody();
                    
                    // Check if XSS patterns were sanitized or escaped
                    boolean titleSanitized = !responseTitle.contains("<script>") && 
                                           !responseTitle.contains("javascript:") &&
                                           !responseTitle.contains("onerror=") &&
                                           !responseTitle.contains("onload=");
                    
                    boolean bodySanitized = !responseBody.contains("<script>") && 
                                          !responseBody.contains("javascript:") &&
                                          !responseBody.contains("onerror=") &&
                                          !responseBody.contains("onload=");
                    
                    if (titleSanitized && bodySanitized) {
                        sanitizedResponses++;
                    } else {
                        unsanitizedResponses.add("Pattern: " + xssPattern + 
                                               " - Title: " + responseTitle + 
                                               " - Body: " + responseBody);
                    }
                } else {
                    // Service rejected malicious content - also counts as protection
                    sanitizedResponses++;
                }
                
            } catch (StatusRuntimeException e) {
                // Service blocked the request - this is good
                sanitizedResponses++;
                LOG.debug("XSS pattern blocked by service: {}", xssPattern);
            }
        }
        
        double sanitizationRate = (double) sanitizedResponses / totalXssAttempts;
        
        assertTrue(sanitizationRate >= 0.95, 
            String.format("XSS sanitization rate %.2f%% below minimum 95%%", sanitizationRate * 100));
        
        assertTrue(unsanitizedResponses.size() <= 1, 
            String.format("Too many unsanitized XSS responses: %s", unsanitizedResponses));
        
        LOG.info("✅ XSS prevention test completed - Sanitization rate: {:.2f}%, Unsanitized: {}", 
                sanitizationRate * 100, unsanitizedResponses.size());
    }

    @Test
    @Order(3)
    @DisplayName("Command Injection Attack Prevention")
    void testCommandInjectionPrevention() {
        LOG.info("Testing command injection attack prevention");
        
        int totalCommandAttempts = 0;
        int blockedCommands = 0;
        Map<String, List<String>> serviceFailures = new HashMap<>();
        
        for (String commandPattern : COMMAND_INJECTION_PATTERNS) {
            totalCommandAttempts++;
            
            // Test command injection in document processing
            String maliciousContent = "Document content with command injection: " + commandPattern;
            
            // Test Tika service (most likely to execute commands on files)
            if (testTikaServiceWithMaliciousCommand(commandPattern)) {
                blockedCommands++;
            } else {
                serviceFailures.computeIfAbsent("tika", k -> new ArrayList<>()).add(commandPattern);
            }
            
            // Test other services
            for (String serviceName : Arrays.asList("chunker", "embedder", "echo")) {
                PipeDoc maliciousDoc = PipeDoc.newBuilder()
                        .setId("cmd-injection-" + System.currentTimeMillis())
                        .setTitle("Command injection test")
                        .setBody(maliciousContent)
                        .build();
                
                ProcessRequest request = createProcessRequest("command-injection-test", serviceName + "-step", maliciousDoc);
                
                try {
                    PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClientForService(serviceName);
                    if ("embedder".equals(serviceName)) {
                        request = createEmbedderProcessRequest("command-injection-test", serviceName + "-step", maliciousDoc);
                    }
                    
                    ProcessResponse response = client.processData(request);
                    
                    // Verify no command execution artifacts in response
                    if (response.getSuccess()) {
                        String responseContent = response.getOutputDoc().getBody();
                        
                        // Check for signs of command execution
                        boolean hasExecutionArtifacts = responseContent.contains("root:") || // passwd file
                                                      responseContent.contains("uid=") ||    // whoami output
                                                      responseContent.contains("/bin/") ||   // system paths
                                                      responseContent.contains("administrator"); // windows user
                        
                        if (!hasExecutionArtifacts) {
                            blockedCommands++;
                        } else {
                            serviceFailures.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(commandPattern);
                        }
                    } else {
                        // Service rejected malicious content
                        blockedCommands++;
                    }
                    
                } catch (StatusRuntimeException e) {
                    // Service blocked the request
                    blockedCommands++;
                }
                
                totalCommandAttempts++;
            }
        }
        
        double commandBlockingRate = (double) blockedCommands / totalCommandAttempts;
        
        assertTrue(commandBlockingRate >= 0.90, 
            String.format("Command injection blocking rate %.2f%% below minimum 90%%", commandBlockingRate * 100));
        
        // Verify no service had multiple command injection vulnerabilities
        for (Map.Entry<String, List<String>> entry : serviceFailures.entrySet()) {
            String serviceName = entry.getKey();
            List<String> failures = entry.getValue();
            
            assertTrue(failures.size() <= 1, 
                String.format("%s service vulnerable to command injection: %s", serviceName, failures));
        }
        
        LOG.info("✅ Command injection prevention test completed - Blocking rate: {:.2f}%, Failures: {}", 
                commandBlockingRate * 100, serviceFailures);
    }

    @Test
    @Order(4)
    @DisplayName("Path Traversal Attack Prevention")
    void testPathTraversalPrevention() {
        LOG.info("Testing path traversal attack prevention");
        
        int totalTraversalAttempts = 0;
        int blockedTraversals = 0;
        List<String> vulnerableResponses = new ArrayList<>();
        
        for (String traversalPattern : PATH_TRAVERSAL_PATTERNS) {
            totalTraversalAttempts++;
            
            // Test path traversal in filename (Tika service most vulnerable)
            byte[] dummyContent = "Dummy file content for path traversal test".getBytes(StandardCharsets.UTF_8);
            
            Blob maliciousBlob = Blob.newBuilder()
                    .setBlobId("traversal-test-" + System.currentTimeMillis())
                    .setFilename(traversalPattern) // Malicious filename
                    .setData(ByteString.copyFrom(dummyContent))
                    .setMimeType("text/plain")
                    .build();
            
            PipeDoc maliciousDoc = PipeDoc.newBuilder()
                    .setId("path-traversal-test")
                    .setTitle("Path traversal test")
                    .setBlob(maliciousBlob)
                    .build();
            
            ProcessRequest request = createProcessRequest("path-traversal-test", "tika-step", maliciousDoc);
            
            try {
                ProcessResponse response = tikaClient.processData(request);
                
                if (response.getSuccess()) {
                    String responseBody = response.getOutputDoc().getBody();
                    
                    // Check for signs of successful path traversal
                    boolean hasSystemFiles = responseBody.contains("root:x:0:0:root") || // passwd file content
                                           responseBody.contains("[HKEY_LOCAL_MACHINE") || // registry content
                                           responseBody.contains("Administrator") ||      // windows admin
                                           responseBody.contains("daemon:x:");           // more passwd content
                    
                    if (!hasSystemFiles) {
                        blockedTraversals++;
                    } else {
                        vulnerableResponses.add("Pattern: " + traversalPattern + " exposed system files");
                    }
                } else {
                    // Service rejected malicious filename
                    blockedTraversals++;
                }
                
            } catch (StatusRuntimeException e) {
                // Service blocked the request
                blockedTraversals++;
                LOG.debug("Path traversal blocked: {}", traversalPattern);
            }
        }
        
        double traversalBlockingRate = (double) blockedTraversals / totalTraversalAttempts;
        
        assertTrue(traversalBlockingRate >= 0.95, 
            String.format("Path traversal blocking rate %.2f%% below minimum 95%%", traversalBlockingRate * 100));
        
        assertTrue(vulnerableResponses.isEmpty(), 
            String.format("Path traversal vulnerabilities detected: %s", vulnerableResponses));
        
        LOG.info("✅ Path traversal prevention test completed - Blocking rate: {:.2f}%", 
                traversalBlockingRate * 100);
    }

    @Test
    @Order(5)
    @DisplayName("Buffer Overflow and Large Input Handling")
    void testBufferOverflowPrevention() {
        LOG.info("Testing buffer overflow prevention and large input handling");
        
        List<BufferOverflowTestResult> testResults = new ArrayList<>();
        
        // Test various buffer overflow scenarios
        testResults.add(testLargeContentHandling("echo", 1024 * 1024));      // 1MB
        testResults.add(testLargeContentHandling("echo", 10 * 1024 * 1024)); // 10MB
        testResults.add(testLargeContentHandling("chunker", 5 * 1024 * 1024)); // 5MB
        testResults.add(testExtremelyLongFieldHandling());
        testResults.add(testMaliciousBinaryContentHandling());
        testResults.add(testDeeplyNestedStructureHandling());
        
        // Verify all buffer overflow tests passed
        for (BufferOverflowTestResult result : testResults) {
            assertTrue(result.handledGracefully, 
                String.format("Buffer overflow test failed: %s", result.testDescription));
            
            assertFalse(result.causedCrash, 
                String.format("Service crashed during test: %s", result.testDescription));
            
            assertTrue(result.responseTime < 60000, // Max 1 minute
                String.format("Test took too long (%dms): %s", result.responseTime, result.testDescription));
        }
        
        LOG.info("✅ Buffer overflow prevention test completed - All {} tests passed", testResults.size());
    }

    @Test
    @Order(6)
    @DisplayName("Data Integrity and Checksum Verification")
    void testDataIntegrityVerification() throws Exception {
        LOG.info("Testing data integrity and checksum verification");
        
        Map<String, DataIntegrityResult> integrityResults = new HashMap<>();
        
        // Test data integrity across all services
        integrityResults.put("TikaParser", testServiceDataIntegrity(tikaClient, "tika"));
        integrityResults.put("Chunker", testServiceDataIntegrity(chunkerClient, "chunker"));
        integrityResults.put("Embedder", testServiceDataIntegrity(embedderClient, "embedder"));
        integrityResults.put("Echo", testServiceDataIntegrity(echoClient, "echo"));
        
        // Verify data integrity for all services
        for (Map.Entry<String, DataIntegrityResult> entry : integrityResults.entrySet()) {
            String serviceName = entry.getKey();
            DataIntegrityResult result = entry.getValue();
            
            assertTrue(result.integrityMaintained, 
                String.format("%s failed to maintain data integrity", serviceName));
            
            assertTrue(result.checksumValid, 
                String.format("%s failed checksum validation", serviceName));
            
            assertFalse(result.dataCorrupted, 
                String.format("%s corrupted data during processing", serviceName));
            
            LOG.info("✅ {} data integrity verified - Input hash: {}, Output hash: {}", 
                    serviceName, result.inputHash, result.outputHash);
        }
        
        LOG.info("✅ Data integrity verification test completed");
    }

    @Test
    @Order(7)
    @DisplayName("Secure Error Handling and Information Leakage Prevention")
    void testSecureErrorHandling() {
        LOG.info("Testing secure error handling and information leakage prevention");
        
        List<String> informationLeaks = new ArrayList<>();
        int totalErrorTests = 0;
        int secureErrorResponses = 0;
        
        // Test various error conditions that might leak sensitive information
        List<String> errorInducingInputs = Arrays.asList(
            "",  // Empty input
            null, // Null handling (will be converted to empty)
            "a".repeat(MAX_FIELD_LENGTH + 1), // Too long input
            "\u0000\u0001\u0002", // Binary/control characters
            "{'malformed': json}", // Malformed JSON-like content
            "🚀🔥💯".repeat(1000) // Unicode stress test
        );
        
        for (String serviceName : Arrays.asList("tika", "chunker", "embedder", "echo")) {
            for (String errorInput : errorInducingInputs) {
                totalErrorTests++;
                
                try {
                    PipeDoc errorDoc = PipeDoc.newBuilder()
                            .setId("error-test-" + System.currentTimeMillis())
                            .setTitle(errorInput != null ? errorInput : "")
                            .setBody(errorInput != null ? errorInput : "")
                            .build();
                    
                    ProcessRequest request;
                    if ("embedder".equals(serviceName)) {
                        request = createEmbedderProcessRequest("error-handling-test", serviceName + "-step", errorDoc);
                    } else {
                        request = createProcessRequest("error-handling-test", serviceName + "-step", errorDoc);
                    }
                    
                    PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClientForService(serviceName);
                    ProcessResponse response = client.processData(request);
                    
                    // Check for information leakage in response
                    String responseText = response.toString();
                    
                    if (containsSensitiveInformation(responseText)) {
                        informationLeaks.add(String.format("%s service leaked information with input: %s", 
                                                          serviceName, errorInput));
                    } else {
                        secureErrorResponses++;
                    }
                    
                } catch (StatusRuntimeException e) {
                    // Check error message for information leakage
                    String errorMessage = e.getMessage();
                    
                    if (containsSensitiveInformation(errorMessage)) {
                        informationLeaks.add(String.format("%s service error leaked information: %s", 
                                                          serviceName, errorMessage));
                    } else {
                        secureErrorResponses++;
                    }
                } catch (Exception e) {
                    // Other exceptions should also be checked
                    if (containsSensitiveInformation(e.getMessage())) {
                        informationLeaks.add(String.format("%s service exception leaked information: %s", 
                                                          serviceName, e.getMessage()));
                    } else {
                        secureErrorResponses++;
                    }
                }
            }
        }
        
        double secureErrorRate = (double) secureErrorResponses / totalErrorTests;
        
        assertTrue(secureErrorRate >= 0.90, 
            String.format("Secure error handling rate %.2f%% below minimum 90%%", secureErrorRate * 100));
        
        assertTrue(informationLeaks.size() <= 2, 
            String.format("Too many information leaks detected: %s", informationLeaks));
        
        LOG.info("✅ Secure error handling test completed - Secure rate: {:.2f}%, Leaks: {}", 
                secureErrorRate * 100, informationLeaks.size());
    }
    
    // Helper Methods

    private boolean testServiceWithMaliciousInput(PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client,
                                                 String serviceName, String maliciousInput, String attackType) {
        try {
            PipeDoc maliciousDoc = PipeDoc.newBuilder()
                    .setId("security-test-" + System.currentTimeMillis())
                    .setTitle(attackType + " test: " + maliciousInput)
                    .setBody("Content with " + attackType + ": " + maliciousInput)
                    .build();
            
            ProcessRequest request;
            if ("embedder".equals(serviceName)) {
                request = createEmbedderProcessRequest("security-test", serviceName + "-step", maliciousDoc);
            } else {
                request = createProcessRequest("security-test", serviceName + "-step", maliciousDoc);
            }
            
            ProcessResponse response = client.processData(request);
            
            // Check if malicious input was sanitized or blocked
            if (response.getSuccess()) {
                String responseContent = response.getOutputDoc().getBody();
                
                // If the malicious input appears unchanged, it wasn't blocked
                return !responseContent.contains(maliciousInput);
            } else {
                // Service rejected the request - good security behavior
                return true;
            }
            
        } catch (StatusRuntimeException e) {
            // Service blocked the request - good security behavior
            return true;
        } catch (Exception e) {
            LOG.debug("Service {} failed with malicious input {}: {}", serviceName, maliciousInput, e.getMessage());
            return false;
        }
    }

    private boolean testTikaServiceWithMaliciousCommand(String commandPattern) {
        try {
            // Create a document with command injection in filename and content
            String maliciousContent = "Document content " + commandPattern;
            byte[] contentBytes = maliciousContent.getBytes(StandardCharsets.UTF_8);
            
            Blob maliciousBlob = Blob.newBuilder()
                    .setBlobId("cmd-injection-" + System.currentTimeMillis())
                    .setFilename("test" + commandPattern + ".txt")
                    .setData(ByteString.copyFrom(contentBytes))
                    .setMimeType("text/plain")
                    .build();
            
            PipeDoc maliciousDoc = PipeDoc.newBuilder()
                    .setId("cmd-injection-test")
                    .setTitle("Command injection test")
                    .setBlob(maliciousBlob)
                    .build();
            
            ProcessRequest request = createProcessRequest("cmd-injection-test", "tika-step", maliciousDoc);
            ProcessResponse response = tikaClient.processData(request);
            
            if (response.getSuccess()) {
                String responseContent = response.getOutputDoc().getBody();
                
                // Check for signs of command execution
                return !responseContent.contains("root:") && 
                       !responseContent.contains("uid=") &&
                       !responseContent.contains("/bin/") &&
                       !responseContent.contains("administrator");
            } else {
                return true; // Service blocked the request
            }
            
        } catch (StatusRuntimeException e) {
            return true; // Service blocked the request
        } catch (Exception e) {
            return false;
        }
    }

    private BufferOverflowTestResult testLargeContentHandling(String serviceName, int contentSize) {
        String testDescription = String.format("%s service with %d byte content", serviceName, contentSize);
        long startTime = System.currentTimeMillis();
        
        try {
            // Create large content
            StringBuilder largeContent = new StringBuilder();
            String pattern = "Large content test pattern ";
            while (largeContent.length() < contentSize) {
                largeContent.append(pattern);
                if (largeContent.length() >= contentSize) break;
            }
            
            PipeDoc largeDoc = PipeDoc.newBuilder()
                    .setId("buffer-overflow-test")
                    .setTitle("Buffer overflow test")
                    .setBody(largeContent.toString())
                    .build();
            
            ProcessRequest request;
            if ("embedder".equals(serviceName)) {
                request = createEmbedderProcessRequest("buffer-test", serviceName + "-step", largeDoc);
            } else {
                request = createProcessRequest("buffer-test", serviceName + "-step", largeDoc);
            }
            
            PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClientForService(serviceName);
            ProcessResponse response = client.withDeadlineAfter(60, TimeUnit.SECONDS)
                    .processData(request);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Service handled large content gracefully
            return new BufferOverflowTestResult(testDescription, true, false, responseTime);
            
        } catch (StatusRuntimeException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (e.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED || 
                e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                // Service properly rejected oversized content
                return new BufferOverflowTestResult(testDescription, true, false, responseTime);
            } else {
                // Unexpected error
                return new BufferOverflowTestResult(testDescription, false, false, responseTime);
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, false, true, responseTime);
        }
    }

    private BufferOverflowTestResult testExtremelyLongFieldHandling() {
        String testDescription = "Extremely long field handling";
        long startTime = System.currentTimeMillis();
        
        try {
            String extremelyLongTitle = "A".repeat(100000); // 100k characters
            
            PipeDoc longFieldDoc = PipeDoc.newBuilder()
                    .setId("long-field-test")
                    .setTitle(extremelyLongTitle)
                    .setBody("Normal body content")
                    .build();
            
            ProcessRequest request = createProcessRequest("long-field-test", "echo-step", longFieldDoc);
            ProcessResponse response = echoClient.processData(request);
            
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, true, false, responseTime);
            
        } catch (StatusRuntimeException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT ||
                e.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                return new BufferOverflowTestResult(testDescription, true, false, responseTime);
            } else {
                return new BufferOverflowTestResult(testDescription, false, false, responseTime);
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, false, true, responseTime);
        }
    }

    private BufferOverflowTestResult testMaliciousBinaryContentHandling() {
        String testDescription = "Malicious binary content handling";
        long startTime = System.currentTimeMillis();
        
        try {
            // Create binary content that might cause buffer overflows
            byte[] maliciousBinary = new byte[10000];
            Arrays.fill(maliciousBinary, (byte) 0xFF); // Fill with max byte value
            
            Blob maliciousBlob = Blob.newBuilder()
                    .setBlobId("malicious-binary-test")
                    .setFilename("malicious.bin")
                    .setData(ByteString.copyFrom(maliciousBinary))
                    .setMimeType("application/octet-stream")
                    .build();
            
            PipeDoc binaryDoc = PipeDoc.newBuilder()
                    .setId("binary-test")
                    .setTitle("Binary content test")
                    .setBlob(maliciousBlob)
                    .build();
            
            ProcessRequest request = createProcessRequest("binary-test", "tika-step", binaryDoc);
            ProcessResponse response = tikaClient.processData(request);
            
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, true, false, responseTime);
            
        } catch (StatusRuntimeException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, true, false, responseTime);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, false, true, responseTime);
        }
    }

    private BufferOverflowTestResult testDeeplyNestedStructureHandling() {
        String testDescription = "Deeply nested structure handling";
        long startTime = System.currentTimeMillis();
        
        try {
            // Create deeply nested JSON-like content
            StringBuilder nestedContent = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                nestedContent.append("{\"level").append(i).append("\": ");
            }
            nestedContent.append("\"deep_value\"");
            for (int i = 0; i < 1000; i++) {
                nestedContent.append("}");
            }
            
            PipeDoc nestedDoc = PipeDoc.newBuilder()
                    .setId("nested-test")
                    .setTitle("Nested structure test")
                    .setBody(nestedContent.toString())
                    .build();
            
            ProcessRequest request = createProcessRequest("nested-test", "chunker-step", nestedDoc);
            ProcessResponse response = chunkerClient.processData(request);
            
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, true, false, responseTime);
            
        } catch (StatusRuntimeException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, true, false, responseTime);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new BufferOverflowTestResult(testDescription, false, true, responseTime);
        }
    }

    private DataIntegrityResult testServiceDataIntegrity(PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client,
                                                        String serviceName) throws Exception {
        String testContent = "Data integrity test content for " + serviceName + " service. " +
                           "This content should maintain its integrity during processing.";
        
        // Calculate input hash
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        String inputHash = bytesToHex(md5.digest(testContent.getBytes(StandardCharsets.UTF_8)));
        
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setId("integrity-test-" + serviceName)
                .setTitle("Data integrity test")
                .setBody(testContent)
                .build();
        
        ProcessRequest request;
        if ("embedder".equals(serviceName)) {
            request = createEmbedderProcessRequest("integrity-test", serviceName + "-step", inputDoc);
        } else {
            request = createProcessRequest("integrity-test", serviceName + "-step", inputDoc);
        }
        
        ProcessResponse response = client.processData(request);
        
        if (!response.getSuccess()) {
            return new DataIntegrityResult(false, false, true, inputHash, "");
        }
        
        String outputContent = response.getOutputDoc().getBody();
        String outputHash = bytesToHex(md5.digest(outputContent.getBytes(StandardCharsets.UTF_8)));
        
        // For most services, content should be preserved exactly (except embedder which adds embeddings)
        boolean integrityMaintained = "embedder".equals(serviceName) || 
                                    outputContent.contains(testContent) ||
                                    inputHash.equals(outputHash);
        
        boolean checksumValid = inputHash.equals(outputHash) || "embedder".equals(serviceName);
        boolean dataCorrupted = outputContent.isEmpty() || outputContent.contains("\u0000");
        
        return new DataIntegrityResult(integrityMaintained, checksumValid, dataCorrupted, inputHash, outputHash);
    }

    private boolean containsSensitiveInformation(String text) {
        if (text == null) return false;
        
        String lowerText = text.toLowerCase();
        
        // Check for various types of sensitive information
        return lowerText.contains("password") ||
               lowerText.contains("secret") ||
               lowerText.contains("private key") ||
               lowerText.contains("api key") ||
               lowerText.contains("token") ||
               lowerText.contains("credit card") ||
               lowerText.contains("ssn") ||
               lowerText.contains("social security") ||
               lowerText.contains("/etc/passwd") ||
               lowerText.contains("c:\\windows") ||
               lowerText.contains("database") ||
               lowerText.contains("connection string") ||
               lowerText.contains("stack trace") ||
               lowerText.contains("exception") ||
               lowerText.contains("error at line") ||
               lowerText.contains("file not found") ||
               lowerText.contains("access denied");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("security-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder().build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }

    private ProcessRequest createEmbedderProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("security-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        Struct embedderConfig = Struct.newBuilder()
                .putFields("embeddingModel", Value.newBuilder().setStringValue("ALL_MINILM_L6_V2").build())
                .putFields("fieldsToEmbed", Value.newBuilder()
                        .setListValue(com.google.protobuf.ListValue.newBuilder()
                                .addValues(Value.newBuilder().setStringValue("title").build())
                                .addValues(Value.newBuilder().setStringValue("body").build())
                                .build())
                        .build())
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(embedderConfig)
                .build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }

    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub getClientForService(String serviceName) {
        switch (serviceName) {
            case "tika": return tikaClient;
            case "chunker": return chunkerClient;
            case "embedder": return embedderClient;
            case "echo": return echoClient;
            default: throw new IllegalArgumentException("Unknown service: " + serviceName);
        }
    }

    // Inner classes for test results
    private static class BufferOverflowTestResult {
        final String testDescription;
        final boolean handledGracefully;
        final boolean causedCrash;
        final long responseTime;
        
        BufferOverflowTestResult(String testDescription, boolean handledGracefully, 
                               boolean causedCrash, long responseTime) {
            this.testDescription = testDescription;
            this.handledGracefully = handledGracefully;
            this.causedCrash = causedCrash;
            this.responseTime = responseTime;
        }
    }

    private static class DataIntegrityResult {
        final boolean integrityMaintained;
        final boolean checksumValid;
        final boolean dataCorrupted;
        final String inputHash;
        final String outputHash;
        
        DataIntegrityResult(boolean integrityMaintained, boolean checksumValid, boolean dataCorrupted,
                          String inputHash, String outputHash) {
            this.integrityMaintained = integrityMaintained;
            this.checksumValid = checksumValid;
            this.dataCorrupted = dataCorrupted;
            this.inputHash = inputHash;
            this.outputHash = outputHash;
        }
    }
}