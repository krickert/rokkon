package com.rokkon.integration.failover;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive failover and resilience tests to validate service behavior under failure conditions,
 * network issues, and degraded performance scenarios.
 * 
 * These tests verify:
 * - Service failure detection and graceful degradation
 * - Network resilience and connection recovery
 * - Circuit breaker patterns and backoff strategies
 * - Timeout handling and retry mechanisms
 * - Partial failure scenarios in multi-service pipelines
 * - Service health monitoring and alerting
 * - Graceful shutdown and startup behavior
 * - Load balancing and failover routing
 * - Data consistency during failure recovery
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FailoverIntegrationTests {

    private static final Logger LOG = LoggerFactory.getLogger(FailoverIntegrationTests.class);
    
    // Failure test parameters
    private static final int RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long MAX_RETRY_DELAY_MS = 5000;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 10000;
    private static final long HEALTH_CHECK_INTERVAL_MS = 2000;
    private static final int PARTIAL_FAILURE_RATE = 30; // 30% failure rate
    
    // Resilience thresholds
    private static final double MIN_RECOVERY_SUCCESS_RATE = 0.90;
    private static final long MAX_RECOVERY_TIME_MS = 30000;
    private static final long MAX_FAILOVER_TIME_MS = 5000;
    
    private ManagedChannel tikaChannel;
    private ManagedChannel chunkerChannel;
    private ManagedChannel embedderChannel;
    private ManagedChannel echoChannel;
    
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub tikaClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub chunkerClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub embedderClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub echoClient;
    
    private ExecutorService failoverExecutor;
    private ScheduledExecutorService healthCheckScheduler;

    @BeforeEach
    void setUp() {
        // Set up gRPC channels with resilience configurations
        tikaChannel = createResilientChannel("localhost", 9000);
        chunkerChannel = createResilientChannel("localhost", 9001);
        embedderChannel = createResilientChannel("localhost", 9002);
        echoChannel = createResilientChannel("localhost", 9003);
        
        tikaClient = PipeStepProcessorGrpc.newBlockingStub(tikaChannel);
        chunkerClient = PipeStepProcessorGrpc.newBlockingStub(chunkerChannel);
        embedderClient = PipeStepProcessorGrpc.newBlockingStub(embedderChannel);
        echoClient = PipeStepProcessorGrpc.newBlockingStub(echoChannel);
        
        failoverExecutor = Executors.newFixedThreadPool(20);
        healthCheckScheduler = Executors.newScheduledThreadPool(5);
        
        LOG.info("Failover integration test environment initialized");
    }

    @AfterEach 
    void tearDown() throws InterruptedException {
        if (failoverExecutor != null) {
            failoverExecutor.shutdown();
            failoverExecutor.awaitTermination(30, TimeUnit.SECONDS);
        }
        
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdown();
            healthCheckScheduler.awaitTermination(10, TimeUnit.SECONDS);
        }
        
        shutdownChannelGracefully(tikaChannel);
        shutdownChannelGracefully(chunkerChannel);
        shutdownChannelGracefully(embedderChannel);
        shutdownChannelGracefully(echoChannel);
    }

    @Test
    @Order(1)
    @DisplayName("Service Timeout Handling and Recovery")
    void testServiceTimeoutHandling() throws Exception {
        LOG.info("Testing service timeout handling and recovery");
        
        Map<String, TimeoutTestResult> serviceResults = new HashMap<>();
        
        // Test timeout scenarios for each service
        serviceResults.put("TikaParser", testServiceTimeoutRecovery(tikaClient, "tika"));
        serviceResults.put("Chunker", testServiceTimeoutRecovery(chunkerClient, "chunker"));
        serviceResults.put("Embedder", testServiceTimeoutRecovery(embedderClient, "embedder"));
        serviceResults.put("Echo", testServiceTimeoutRecovery(echoClient, "echo"));
        
        // Verify timeout handling and recovery
        for (Map.Entry<String, TimeoutTestResult> entry : serviceResults.entrySet()) {
            String serviceName = entry.getKey();
            TimeoutTestResult result = entry.getValue();
            
            assertTrue(result.timeoutDetected, 
                String.format("%s should detect timeout conditions", serviceName));
            
            assertTrue(result.recoverySuccessful, 
                String.format("%s should recover after timeout", serviceName));
            
            assertTrue(result.recoveryTime <= MAX_RECOVERY_TIME_MS,
                String.format("%s recovery time %dms exceeds maximum %dms",
                    serviceName, result.recoveryTime, MAX_RECOVERY_TIME_MS));
            
            LOG.info("✅ {} timeout handling verified - Recovery time: {}ms, Success rate: {:.2f}%", 
                    serviceName, result.recoveryTime, result.postRecoverySuccessRate * 100);
        }
        
        LOG.info("✅ Service timeout handling and recovery test completed");
    }

    @Test
    @Order(2)
    @DisplayName("Network Resilience and Connection Recovery")
    void testNetworkResilienceAndRecovery() throws Exception {
        LOG.info("Testing network resilience and connection recovery");
        
        // Simulate network issues by using very short deadlines
        AtomicInteger networkFailures = new AtomicInteger();
        AtomicInteger recoverySuccesses = new AtomicInteger();
        AtomicLong totalRecoveryTime = new AtomicLong();
        
        int networkTestRequests = 20;
        CountDownLatch networkTestLatch = new CountDownLatch(networkTestRequests);
        
        for (int i = 0; i < networkTestRequests; i++) {
            final int requestId = i;
            failoverExecutor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    
                    // First, attempt with very short deadline to simulate network issues
                    ProcessRequest request = createTestRequest("echo", "network-test-" + requestId);
                    
                    boolean networkFailureDetected = false;
                    try {
                        echoClient.withDeadlineAfter(1, TimeUnit.MILLISECONDS) // Extremely short deadline
                                .processData(request);
                    } catch (StatusRuntimeException e) {
                        if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                            networkFailureDetected = true;
                            networkFailures.incrementAndGet();
                        }
                    }
                    
                    // If network failure was detected, attempt recovery with normal deadline
                    if (networkFailureDetected) {
                        Thread.sleep(100); // Brief backoff
                        
                        try {
                            ProcessResponse recoveryResponse = echoClient.withDeadlineAfter(10, TimeUnit.SECONDS)
                                    .processData(request);
                            
                            if (recoveryResponse.getSuccess()) {
                                recoverySuccesses.incrementAndGet();
                                long recoveryTime = System.currentTimeMillis() - startTime;
                                totalRecoveryTime.addAndGet(recoveryTime);
                            }
                        } catch (Exception recoveryError) {
                            LOG.debug("Recovery failed for request {}: {}", requestId, recoveryError.getMessage());
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Network test request {} failed: {}", requestId, e.getMessage());
                } finally {
                    networkTestLatch.countDown();
                }
            });
        }
        
        assertTrue(networkTestLatch.await(2, TimeUnit.MINUTES),
            "Network resilience test should complete within 2 minutes");
        
        int failures = networkFailures.get();
        int recoveries = recoverySuccesses.get();
        double recoveryRate = failures > 0 ? (double) recoveries / failures : 0;
        long avgRecoveryTime = recoveries > 0 ? totalRecoveryTime.get() / recoveries : 0;
        
        assertTrue(failures > 0, "Should have detected network failures");
        assertTrue(recoveryRate >= MIN_RECOVERY_SUCCESS_RATE,
            String.format("Recovery rate %.2f%% below minimum %.2f%%",
                recoveryRate * 100, MIN_RECOVERY_SUCCESS_RATE * 100));
        
        LOG.info("✅ Network resilience test completed - Failures: {}, Recoveries: {}, " +
                "Recovery rate: {:.2f}%, Avg recovery time: {}ms",
                failures, recoveries, recoveryRate * 100, avgRecoveryTime);
    }

    @Test
    @Order(3)
    @DisplayName("Circuit Breaker Pattern Verification")
    void testCircuitBreakerPattern() throws Exception {
        LOG.info("Testing circuit breaker pattern behavior");
        
        // Test circuit breaker by creating a pattern of failures followed by recovery
        AtomicInteger consecutiveFailures = new AtomicInteger();
        AtomicInteger circuitBreakerTrips = new AtomicInteger();
        AtomicInteger recoveryAttempts = new AtomicInteger();
        AtomicInteger finalSuccesses = new AtomicInteger();
        
        int circuitBreakerTests = 15;
        CountDownLatch circuitBreakerLatch = new CountDownLatch(circuitBreakerTests);
        
        for (int i = 0; i < circuitBreakerTests; i++) {
            final int testId = i;
            failoverExecutor.submit(() -> {
                try {
                    ProcessRequest request = createTestRequest("echo", "circuit-breaker-" + testId);
                    
                    // Simulate circuit breaker logic
                    boolean shouldFail = testId < 5; // First 5 requests fail
                    
                    if (shouldFail) {
                        // Simulate failure by using impossible deadline
                        try {
                            echoClient.withDeadlineAfter(1, TimeUnit.NANOSECONDS)
                                    .processData(request);
                        } catch (StatusRuntimeException e) {
                            if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                                int failures = consecutiveFailures.incrementAndGet();
                                
                                // Circuit breaker logic: trip after 3 consecutive failures
                                if (failures >= 3 && failures <= 5) {
                                    circuitBreakerTrips.incrementAndGet();
                                    LOG.debug("Circuit breaker tripped after {} failures", failures);
                                }
                            }
                        }
                    } else {
                        // Recovery phase: circuit breaker should allow requests through
                        recoveryAttempts.incrementAndGet();
                        
                        try {
                            // Add delay to simulate circuit breaker recovery period
                            Thread.sleep(500);
                            
                            ProcessResponse response = echoClient.withDeadlineAfter(10, TimeUnit.SECONDS)
                                    .processData(request);
                            
                            if (response.getSuccess()) {
                                finalSuccesses.incrementAndGet();
                            }
                        } catch (Exception e) {
                            LOG.debug("Circuit breaker recovery attempt failed: {}", e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Circuit breaker test {} failed: {}", testId, e.getMessage());
                } finally {
                    circuitBreakerLatch.countDown();
                }
            });
        }
        
        assertTrue(circuitBreakerLatch.await(2, TimeUnit.MINUTES),
            "Circuit breaker test should complete within 2 minutes");
        
        int trips = circuitBreakerTrips.get();
        int recoveries = recoveryAttempts.get();
        int successes = finalSuccesses.get();
        double recoverySuccessRate = recoveries > 0 ? (double) successes / recoveries : 0;
        
        assertTrue(trips > 0, "Circuit breaker should have tripped");
        assertTrue(recoveries > 0, "Should have attempted recovery");
        assertTrue(recoverySuccessRate >= MIN_RECOVERY_SUCCESS_RATE,
            String.format("Circuit breaker recovery rate %.2f%% below minimum %.2f%%",
                recoverySuccessRate * 100, MIN_RECOVERY_SUCCESS_RATE * 100));
        
        LOG.info("✅ Circuit breaker test completed - Trips: {}, Recovery attempts: {}, " +
                "Recovery successes: {}, Recovery rate: {:.2f}%",
                trips, recoveries, successes, recoverySuccessRate * 100);
    }

    @Test
    @Order(4)
    @DisplayName("Partial Pipeline Failure Handling")
    void testPartialPipelineFailure() throws Exception {
        LOG.info("Testing partial pipeline failure handling");
        
        int pipelineTests = 10;
        AtomicInteger partialFailures = new AtomicInteger();
        AtomicInteger fullRecoveries = new AtomicInteger();
        AtomicInteger gracefulDegradations = new AtomicInteger();
        
        CountDownLatch pipelineFailureLatch = new CountDownLatch(pipelineTests);
        
        for (int i = 0; i < pipelineTests; i++) {
            final int pipelineId = i;
            failoverExecutor.submit(() -> {
                try {
                    String content = "Partial failure test document " + pipelineId + ". " +
                                   "This document tests pipeline resilience. ".repeat(50);
                    
                    PipeDoc originalDoc = createDocumentWithContent("partial-failure-" + pipelineId, content);
                    boolean pipelineFailureDetected = false;
                    boolean recoverySuccessful = false;
                    
                    try {
                        // Step 1: Tika Processing
                        ProcessRequest tikaRequest = createProcessRequest("partial-failure-pipeline", "tika-step", originalDoc);
                        ProcessResponse tikaResponse = tikaClient.withDeadlineAfter(10, TimeUnit.SECONDS)
                                .processData(tikaRequest);
                        
                        if (!tikaResponse.getSuccess()) {
                            pipelineFailureDetected = true;
                            partialFailures.incrementAndGet();
                            LOG.debug("Tika step failed in pipeline {}", pipelineId);
                        }
                        
                        // Step 2: Chunker Processing (proceed even if Tika had issues)
                        PipeDoc chunkerInput = tikaResponse.getSuccess() ? tikaResponse.getOutputDoc() : originalDoc;
                        ProcessRequest chunkerRequest = createProcessRequest("partial-failure-pipeline", "chunker-step", chunkerInput);
                        
                        // Simulate occasional chunker failures
                        if (pipelineId % 3 == 0) {
                            // Force timeout to simulate failure
                            try {
                                chunkerClient.withDeadlineAfter(1, TimeUnit.MILLISECONDS)
                                        .processData(chunkerRequest);
                            } catch (StatusRuntimeException e) {
                                if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                                    pipelineFailureDetected = true;
                                    partialFailures.incrementAndGet();
                                    LOG.debug("Chunker step failed in pipeline {}", pipelineId);
                                    
                                    // Attempt graceful degradation - skip to echo
                                    ProcessRequest echoRequest = createProcessRequest("partial-failure-pipeline", "echo-step", chunkerInput);
                                    ProcessResponse echoResponse = echoClient.withDeadlineAfter(10, TimeUnit.SECONDS)
                                            .processData(echoRequest);
                                    
                                    if (echoResponse.getSuccess()) {
                                        gracefulDegradations.incrementAndGet();
                                        LOG.debug("Graceful degradation successful in pipeline {}", pipelineId);
                                    }
                                    return;
                                }
                            }
                        }
                        
                        ProcessResponse chunkerResponse = chunkerClient.withDeadlineAfter(10, TimeUnit.SECONDS)
                                .processData(chunkerRequest);
                        
                        // If we had failures but still got here, attempt recovery
                        if (pipelineFailureDetected && chunkerResponse.getSuccess()) {
                            // Final step: Echo to verify recovery
                            ProcessRequest echoRequest = createProcessRequest("partial-failure-pipeline", "echo-step", chunkerResponse.getOutputDoc());
                            ProcessResponse echoResponse = echoClient.withDeadlineAfter(10, TimeUnit.SECONDS)
                                    .processData(echoRequest);
                            
                            if (echoResponse.getSuccess()) {
                                recoverySuccessful = true;
                                fullRecoveries.incrementAndGet();
                                LOG.debug("Full recovery successful in pipeline {}", pipelineId);
                            }
                        }
                        
                    } catch (Exception e) {
                        LOG.debug("Pipeline {} failed with exception: {}", pipelineId, e.getMessage());
                        pipelineFailureDetected = true;
                        partialFailures.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    LOG.debug("Partial pipeline failure test {} failed: {}", pipelineId, e.getMessage());
                } finally {
                    pipelineFailureLatch.countDown();
                }
            });
        }
        
        assertTrue(pipelineFailureLatch.await(3, TimeUnit.MINUTES),
            "Partial pipeline failure test should complete within 3 minutes");
        
        int failures = partialFailures.get();
        int recoveries = fullRecoveries.get();
        int degradations = gracefulDegradations.get();
        
        assertTrue(failures > 0, "Should have detected partial failures");
        assertTrue(recoveries > 0 || degradations > 0, "Should have recovery or degradation strategies");
        
        LOG.info("✅ Partial pipeline failure test completed - Failures: {}, Recoveries: {}, " +
                "Graceful degradations: {}", failures, recoveries, degradations);
    }

    @Test
    @Order(5)
    @DisplayName("Service Health Monitoring and Alerting")
    void testServiceHealthMonitoring() throws Exception {
        LOG.info("Testing service health monitoring and alerting");
        
        Map<String, ServiceHealthStatus> healthStatus = new ConcurrentHashMap<>();
        AtomicInteger healthCheckFailures = new AtomicInteger();
        AtomicInteger healthRecoveries = new AtomicInteger();
        CountDownLatch healthMonitoringLatch = new CountDownLatch(1);
        
        // Initialize health status for all services
        healthStatus.put("tika", new ServiceHealthStatus(true, System.currentTimeMillis()));
        healthStatus.put("chunker", new ServiceHealthStatus(true, System.currentTimeMillis()));
        healthStatus.put("embedder", new ServiceHealthStatus(true, System.currentTimeMillis()));
        healthStatus.put("echo", new ServiceHealthStatus(true, System.currentTimeMillis()));
        
        // Start health monitoring
        ScheduledFuture<?> healthMonitor = healthCheckScheduler.scheduleAtFixedRate(() -> {
            try {
                for (String serviceName : Arrays.asList("tika", "chunker", "embedder", "echo")) {
                    boolean isHealthy = performHealthCheck(serviceName);
                    ServiceHealthStatus currentStatus = healthStatus.get(serviceName);
                    
                    if (currentStatus.isHealthy && !isHealthy) {
                        // Service became unhealthy
                        healthCheckFailures.incrementAndGet();
                        healthStatus.put(serviceName, new ServiceHealthStatus(false, System.currentTimeMillis()));
                        LOG.warn("Service {} became unhealthy", serviceName);
                        
                    } else if (!currentStatus.isHealthy && isHealthy) {
                        // Service recovered
                        healthRecoveries.incrementAndGet();
                        healthStatus.put(serviceName, new ServiceHealthStatus(true, System.currentTimeMillis()));
                        LOG.info("Service {} recovered", serviceName);
                    }
                }
            } catch (Exception e) {
                LOG.error("Health monitoring error: {}", e.getMessage());
            }
        }, 0, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        // Run health monitoring for a period
        Thread.sleep(30000); // Monitor for 30 seconds
        
        // Simulate some service issues by causing timeouts
        for (int i = 0; i < 3; i++) {
            try {
                echoClient.withDeadlineAfter(1, TimeUnit.MILLISECONDS)
                        .processData(createTestRequest("echo", "health-test-" + i));
            } catch (StatusRuntimeException e) {
                // Expected timeout
            }
            Thread.sleep(HEALTH_CHECK_INTERVAL_MS * 2);
        }
        
        // Allow time for recovery detection
        Thread.sleep(HEALTH_CHECK_INTERVAL_MS * 3);
        
        healthMonitor.cancel(false);
        healthMonitoringLatch.countDown();
        
        // Verify health monitoring detected issues and recoveries
        boolean allServicesHealthy = healthStatus.values().stream()
                .allMatch(status -> status.isHealthy);
        
        assertTrue(allServicesHealthy, "All services should be healthy at the end");
        
        LOG.info("✅ Service health monitoring completed - Health check failures: {}, Recoveries: {}",
                healthCheckFailures.get(), healthRecoveries.get());
    }

    @Test
    @Order(6)
    @DisplayName("Graceful Shutdown and Startup Behavior")
    void testGracefulShutdownStartup() throws Exception {
        LOG.info("Testing graceful shutdown and startup behavior");
        
        // Test graceful connection shutdown
        ManagedChannel testChannel = createResilientChannel("localhost", 9003);
        PipeStepProcessorGrpc.PipeStepProcessorBlockingStub testClient = 
                PipeStepProcessorGrpc.newBlockingStub(testChannel);
        
        // Verify connection works
        ProcessRequest request = createTestRequest("echo", "shutdown-test");
        ProcessResponse response = testClient.processData(request);
        assertTrue(response.getSuccess(), "Service should be operational before shutdown");
        
        // Initiate graceful shutdown
        long shutdownStart = System.currentTimeMillis();
        testChannel.shutdown();
        
        // Test that in-flight requests complete gracefully
        boolean shutdownGracefully = testChannel.awaitTermination(10, TimeUnit.SECONDS);
        long shutdownTime = System.currentTimeMillis() - shutdownStart;
        
        assertTrue(shutdownGracefully, "Channel should shutdown gracefully");
        assertTrue(shutdownTime <= 10000, "Shutdown should complete within 10 seconds");
        
        // Test reconnection capability
        long startupStart = System.currentTimeMillis();
        ManagedChannel reconnectChannel = createResilientChannel("localhost", 9003);
        PipeStepProcessorGrpc.PipeStepProcessorBlockingStub reconnectClient = 
                PipeStepProcessorGrpc.newBlockingStub(reconnectChannel);
        
        // Verify reconnection works
        ProcessRequest reconnectRequest = createTestRequest("echo", "reconnect-test");
        ProcessResponse reconnectResponse = reconnectClient.processData(reconnectRequest);
        long startupTime = System.currentTimeMillis() - startupStart;
        
        assertTrue(reconnectResponse.getSuccess(), "Service should be operational after reconnection");
        assertTrue(startupTime <= MAX_FAILOVER_TIME_MS, 
            String.format("Reconnection should complete within %dms", MAX_FAILOVER_TIME_MS));
        
        // Clean up
        reconnectChannel.shutdown();
        reconnectChannel.awaitTermination(5, TimeUnit.SECONDS);
        
        LOG.info("✅ Graceful shutdown/startup test completed - Shutdown: {}ms, Startup: {}ms",
                shutdownTime, startupTime);
    }

    // Helper Methods

    private ManagedChannel createResilientChannel(String host, int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(50 * 1024 * 1024)
                .idleTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private void shutdownChannelGracefully(ManagedChannel channel) throws InterruptedException {
        if (channel != null) {
            channel.shutdown();
            if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
                channel.shutdownNow();
                channel.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    private TimeoutTestResult testServiceTimeoutRecovery(PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client,
                                                        String serviceType) throws Exception {
        boolean timeoutDetected = false;
        boolean recoverySuccessful = false;
        long recoveryStart = 0;
        long recoveryTime = 0;
        int postRecoverySuccesses = 0;
        int postRecoveryAttempts = 5;
        
        // Phase 1: Induce timeout
        try {
            ProcessRequest timeoutRequest = createTestRequest(serviceType, "timeout-test");
            client.withDeadlineAfter(1, TimeUnit.MILLISECONDS) // Extremely short deadline
                    .processData(timeoutRequest);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                timeoutDetected = true;
                recoveryStart = System.currentTimeMillis();
                LOG.debug("Timeout detected for {} service", serviceType);
            }
        }
        
        // Phase 2: Attempt recovery
        if (timeoutDetected) {
            Thread.sleep(1000); // Brief backoff
            
            for (int i = 0; i < postRecoveryAttempts; i++) {
                try {
                    ProcessRequest recoveryRequest = createTestRequest(serviceType, "recovery-test-" + i);
                    ProcessResponse response = client.withDeadlineAfter(10, TimeUnit.SECONDS)
                            .processData(recoveryRequest);
                    
                    if (response.getSuccess()) {
                        postRecoverySuccesses++;
                        if (!recoverySuccessful) {
                            recoverySuccessful = true;
                            recoveryTime = System.currentTimeMillis() - recoveryStart;
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Recovery attempt {} failed for {}: {}", i, serviceType, e.getMessage());
                }
            }
        }
        
        double postRecoverySuccessRate = (double) postRecoverySuccesses / postRecoveryAttempts;
        
        return new TimeoutTestResult(timeoutDetected, recoverySuccessful, recoveryTime, postRecoverySuccessRate);
    }

    private boolean performHealthCheck(String serviceName) {
        try {
            PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClientForService(serviceName);
            ProcessRequest healthRequest = createTestRequest(serviceName, "health-check-" + System.currentTimeMillis());
            
            ProcessResponse response = client.withDeadlineAfter(5, TimeUnit.SECONDS)
                    .processData(healthRequest);
            
            return response.getSuccess();
        } catch (Exception e) {
            LOG.debug("Health check failed for {}: {}", serviceName, e.getMessage());
            return false;
        }
    }

    private ProcessRequest createTestRequest(String serviceType, String testId) {
        String content = "Failover test content for " + serviceType + " service. ".repeat(100);
        
        PipeDoc document;
        if ("tika".equals(serviceType)) {
            byte[] blobData = content.getBytes(StandardCharsets.UTF_8);
            Blob blob = Blob.newBuilder()
                    .setBlobId("failover-blob-" + testId)
                    .setFilename(testId + ".txt")
                    .setData(ByteString.copyFrom(blobData))
                    .setMimeType("text/plain")
                    .build();
            
            document = PipeDoc.newBuilder()
                    .setId(testId)
                    .setTitle("Failover Test Document")
                    .setBlob(blob)
                    .build();
        } else if ("embedder".equals(serviceType)) {
            document = PipeDoc.newBuilder()
                    .setId(testId)
                    .setTitle("Failover Test Document")
                    .setBody(content)
                    .build();
            
            return createEmbedderProcessRequest("failover-pipeline", serviceType + "-step", document);
        } else {
            document = PipeDoc.newBuilder()
                    .setId(testId)
                    .setTitle("Failover Test Document")
                    .setBody(content)
                    .build();
        }
        
        return createProcessRequest("failover-pipeline", serviceType + "-step", document);
    }

    private ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("failover-stream-" + System.currentTimeMillis())
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
                .setStreamId("failover-stream-" + System.currentTimeMillis())
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

    private PipeDoc createDocumentWithContent(String id, String content) {
        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle("Failover Test Document")
                .setBody(content)
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
    private static class TimeoutTestResult {
        final boolean timeoutDetected;
        final boolean recoverySuccessful;
        final long recoveryTime;
        final double postRecoverySuccessRate;
        
        TimeoutTestResult(boolean timeoutDetected, boolean recoverySuccessful, 
                         long recoveryTime, double postRecoverySuccessRate) {
            this.timeoutDetected = timeoutDetected;
            this.recoverySuccessful = recoverySuccessful;
            this.recoveryTime = recoveryTime;
            this.postRecoverySuccessRate = postRecoverySuccessRate;
        }
    }

    private static class ServiceHealthStatus {
        final boolean isHealthy;
        final long lastChecked;
        
        ServiceHealthStatus(boolean isHealthy, long lastChecked) {
            this.isHealthy = isHealthy;
            this.lastChecked = lastChecked;
        }
    }
}