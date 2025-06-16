package com.rokkon.integration.concurrency;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * High-load concurrency stress tests to validate thread safety, resource contention handling,
 * and scalability under extreme concurrent loads across all services.
 * 
 * These tests verify:
 * - Thread safety of all gRPC service implementations
 * - Resource contention handling and fair resource allocation
 * - Deadlock prevention and detection
 * - Connection pooling efficiency under high load
 * - Memory consistency under concurrent access
 * - Performance degradation patterns under stress
 * - Service stability during sustained high concurrency
 * - Error recovery and circuit breaker patterns
 */
@Disabled("Requires gRPC services to be running and comprehensive infrastructure setup")
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyStressTests {

    private static final Logger LOG = LoggerFactory.getLogger(ConcurrencyStressTests.class);
    
    // Concurrency test parameters
    private static final int HIGH_CONCURRENCY_THREADS = 50;
    private static final int EXTREME_CONCURRENCY_THREADS = 100;
    private static final int SUSTAINED_LOAD_THREADS = 20;
    private static final int SUSTAINED_LOAD_MINUTES = 5;
    private static final int BURST_LOAD_THREADS = 200;
    private static final int BURST_REQUESTS_PER_THREAD = 10;
    
    // Performance thresholds
    private static final long MAX_AVERAGE_RESPONSE_TIME_MS = 5000;
    private static final double MIN_SUCCESS_RATE = 0.95; // 95% success rate
    private static final long MAX_P99_RESPONSE_TIME_MS = 15000;
    private static final int MAX_TIMEOUT_COUNT = 5; // Max timeouts per test
    
    private ManagedChannel tikaChannel;
    private ManagedChannel chunkerChannel;
    private ManagedChannel embedderChannel;
    private ManagedChannel echoChannel;
    
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub tikaClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub chunkerClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub embedderClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub echoClient;
    
    private ExecutorService testExecutorService;

    @BeforeEach
    void setUp() {
        // Set up gRPC channels for all services
        tikaChannel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext()
                .maxInboundMessageSize(50 * 1024 * 1024) // 50MB for large documents
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
                
        chunkerChannel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext()
                .maxInboundMessageSize(50 * 1024 * 1024)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
                
        embedderChannel = ManagedChannelBuilder.forAddress("localhost", 9002).usePlaintext()
                .maxInboundMessageSize(50 * 1024 * 1024)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
                
        echoChannel = ManagedChannelBuilder.forAddress("localhost", 9003).usePlaintext()
                .maxInboundMessageSize(50 * 1024 * 1024)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        
        tikaClient = PipeStepProcessorGrpc.newBlockingStub(tikaChannel);
        chunkerClient = PipeStepProcessorGrpc.newBlockingStub(chunkerChannel);
        embedderClient = PipeStepProcessorGrpc.newBlockingStub(embedderChannel);
        echoClient = PipeStepProcessorGrpc.newBlockingStub(echoChannel);
        
        // Create thread pool for test execution
        testExecutorService = Executors.newFixedThreadPool(EXTREME_CONCURRENCY_THREADS + 50);
        
        LOG.info("Concurrency stress test environment initialized");
    }

    @AfterEach 
    void tearDown() throws InterruptedException {
        if (testExecutorService != null) {
            testExecutorService.shutdown();
            testExecutorService.awaitTermination(30, TimeUnit.SECONDS);
        }
        
        if (tikaChannel != null) {
            tikaChannel.shutdown();
            tikaChannel.awaitTermination(10, TimeUnit.SECONDS);
        }
        if (chunkerChannel != null) {
            chunkerChannel.shutdown();
            chunkerChannel.awaitTermination(10, TimeUnit.SECONDS);
        }
        if (embedderChannel != null) {
            embedderChannel.shutdown();
            embedderChannel.awaitTermination(10, TimeUnit.SECONDS);
        }
        if (echoChannel != null) {
            echoChannel.shutdown();
            echoChannel.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(1)
    @DisplayName("High Concurrency Thread Safety Test - All Services")
    void testHighConcurrencyThreadSafety() throws Exception {
        LOG.info("Starting high concurrency thread safety test with {} threads", HIGH_CONCURRENCY_THREADS);
        
        Map<String, ConcurrencyTestResult> serviceResults = new HashMap<>();
        
        // Test each service under high concurrency
        serviceResults.put("TikaParser", testServiceConcurrency(tikaClient, "tika", HIGH_CONCURRENCY_THREADS, 5));
        serviceResults.put("Chunker", testServiceConcurrency(chunkerClient, "chunker", HIGH_CONCURRENCY_THREADS, 5));
        serviceResults.put("Embedder", testServiceConcurrency(embedderClient, "embedder", HIGH_CONCURRENCY_THREADS, 3));
        serviceResults.put("Echo", testServiceConcurrency(echoClient, "echo", HIGH_CONCURRENCY_THREADS, 10));
        
        // Verify all services maintained thread safety
        for (Map.Entry<String, ConcurrencyTestResult> entry : serviceResults.entrySet()) {
            String serviceName = entry.getKey();
            ConcurrencyTestResult result = entry.getValue();
            
            assertTrue(result.successRate >= MIN_SUCCESS_RATE, 
                String.format("%s success rate %.2f%% below minimum %.2f%%", 
                    serviceName, result.successRate * 100, MIN_SUCCESS_RATE * 100));
            
            assertTrue(result.averageResponseTime <= MAX_AVERAGE_RESPONSE_TIME_MS,
                String.format("%s average response time %dms exceeds maximum %dms",
                    serviceName, result.averageResponseTime, MAX_AVERAGE_RESPONSE_TIME_MS));
            
            assertTrue(result.timeoutCount <= MAX_TIMEOUT_COUNT,
                String.format("%s timeout count %d exceeds maximum %d",
                    serviceName, result.timeoutCount, MAX_TIMEOUT_COUNT));
            
            LOG.info("✅ {} concurrency test passed - Success: {:.2f}%, Avg: {}ms, P99: {}ms, Timeouts: {}", 
                    serviceName, result.successRate * 100, result.averageResponseTime, 
                    result.p99ResponseTime, result.timeoutCount);
        }
        
        LOG.info("✅ High concurrency thread safety test completed - all services passed");
    }

    @Test
    @Order(2)
    @DisplayName("Extreme Concurrency Stress Test - Resource Contention")
    void testExtremeConcurrencyStress() throws Exception {
        LOG.info("Starting extreme concurrency stress test with {} threads", EXTREME_CONCURRENCY_THREADS);
        
        // Focus on the most resource-intensive service for extreme testing
        ConcurrencyTestResult embedderResult = testServiceConcurrency(embedderClient, "embedder", 
                EXTREME_CONCURRENCY_THREADS, 2);
        
        // Under extreme load, we allow slightly relaxed success rates but verify no complete failures
        assertTrue(embedderResult.successRate >= 0.80, 
            String.format("Embedder success rate %.2f%% too low under extreme load", 
                embedderResult.successRate * 100));
        
        assertTrue(embedderResult.averageResponseTime <= MAX_AVERAGE_RESPONSE_TIME_MS * 2,
            String.format("Embedder response time %dms excessive under extreme load",
                embedderResult.averageResponseTime));
        
        // Verify system didn't completely fail
        assertTrue(embedderResult.completedRequests > 0, "System should handle some requests under extreme load");
        
        LOG.info("✅ Extreme concurrency test completed - Success: {:.2f}%, Completed: {}/{}", 
                embedderResult.successRate * 100, embedderResult.completedRequests, embedderResult.totalRequests);
    }

    @Test
    @Order(3)
    @DisplayName("Sustained Load Stress Test - Long Duration Performance")
    void testSustainedLoadStress() throws Exception {
        LOG.info("Starting sustained load test - {} threads for {} minutes", 
                SUSTAINED_LOAD_THREADS, SUSTAINED_LOAD_MINUTES);
        
        long testDurationMs = SUSTAINED_LOAD_MINUTES * 60 * 1000;
        long startTime = System.currentTimeMillis();
        AtomicLong totalRequests = new AtomicLong();
        AtomicLong successfulRequests = new AtomicLong();
        AtomicInteger activeThreads = new AtomicInteger(SUSTAINED_LOAD_THREADS);
        
        CountDownLatch sustainedTestLatch = new CountDownLatch(SUSTAINED_LOAD_THREADS);
        
        // Launch sustained load threads
        for (int i = 0; i < SUSTAINED_LOAD_THREADS; i++) {
            final int threadId = i;
            testExecutorService.submit(() -> {
                try {
                    while (System.currentTimeMillis() - startTime < testDurationMs) {
                        try {
                            // Rotate through different services for varied load
                            String serviceType = getServiceTypeForThread(threadId);
                            ProcessRequest request = createTestRequest(serviceType, "sustained-load-" + threadId);
                            
                            PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClientForService(serviceType);
                            ProcessResponse response = client.processData(request);
                            
                            totalRequests.incrementAndGet();
                            if (response.getSuccess()) {
                                successfulRequests.incrementAndGet();
                            }
                            
                            // Small delay to prevent overwhelming
                            Thread.sleep(100);
                            
                        } catch (Exception e) {
                            totalRequests.incrementAndGet();
                            LOG.debug("Sustained load request failed: {}", e.getMessage());
                        }
                    }
                } finally {
                    activeThreads.decrementAndGet();
                    sustainedTestLatch.countDown();
                }
            });
        }
        
        // Wait for completion
        assertTrue(sustainedTestLatch.await(SUSTAINED_LOAD_MINUTES + 2, TimeUnit.MINUTES),
            "Sustained load test should complete within timeout");
        
        long totalReq = totalRequests.get();
        long successReq = successfulRequests.get();
        double sustainedSuccessRate = (double) successReq / totalReq;
        
        assertTrue(sustainedSuccessRate >= MIN_SUCCESS_RATE,
            String.format("Sustained load success rate %.2f%% below minimum %.2f%%",
                sustainedSuccessRate * 100, MIN_SUCCESS_RATE * 100));
        
        assertTrue(totalReq > 0, "Should have processed requests during sustained load");
        
        LOG.info("✅ Sustained load test completed - Total: {}, Successful: {}, Success Rate: {:.2f}%", 
                totalReq, successReq, sustainedSuccessRate * 100);
    }

    @Test
    @Order(4)
    @DisplayName("Burst Load Test - Sudden Traffic Spikes")
    void testBurstLoadHandling() throws Exception {
        LOG.info("Starting burst load test - {} threads with {} requests each", 
                BURST_LOAD_THREADS, BURST_REQUESTS_PER_THREAD);
        
        CountDownLatch burstLatch = new CountDownLatch(BURST_LOAD_THREADS);
        AtomicLong burstTotalRequests = new AtomicLong();
        AtomicLong burstSuccessfulRequests = new AtomicLong();
        AtomicLong burstTotalTime = new AtomicLong();
        
        long burstStartTime = System.currentTimeMillis();
        
        // Launch burst load - all threads start simultaneously
        for (int i = 0; i < BURST_LOAD_THREADS; i++) {
            final int threadId = i;
            testExecutorService.submit(() -> {
                try {
                    for (int j = 0; j < BURST_REQUESTS_PER_THREAD; j++) {
                        try {
                            long requestStart = System.currentTimeMillis();
                            
                            // Use echo service for burst testing (fastest response)
                            ProcessRequest request = createTestRequest("echo", "burst-load-" + threadId + "-" + j);
                            ProcessResponse response = echoClient.processData(request);
                            
                            long requestTime = System.currentTimeMillis() - requestStart;
                            burstTotalTime.addAndGet(requestTime);
                            burstTotalRequests.incrementAndGet();
                            
                            if (response.getSuccess()) {
                                burstSuccessfulRequests.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            burstTotalRequests.incrementAndGet();
                            LOG.debug("Burst request failed: {}", e.getMessage());
                        }
                    }
                } finally {
                    burstLatch.countDown();
                }
            });
        }
        
        // Wait for burst completion
        assertTrue(burstLatch.await(2, TimeUnit.MINUTES),
            "Burst load test should complete within 2 minutes");
        
        long burstDuration = System.currentTimeMillis() - burstStartTime;
        long totalBurstReq = burstTotalRequests.get();
        long successBurstReq = burstSuccessfulRequests.get();
        double burstSuccessRate = (double) successBurstReq / totalBurstReq;
        double averageBurstResponseTime = (double) burstTotalTime.get() / totalBurstReq;
        double throughput = (double) totalBurstReq / (burstDuration / 1000.0);
        
        assertTrue(burstSuccessRate >= 0.90, // Allow 90% for burst conditions
            String.format("Burst load success rate %.2f%% too low", burstSuccessRate * 100));
        
        assertTrue(averageBurstResponseTime <= MAX_AVERAGE_RESPONSE_TIME_MS,
            String.format("Burst average response time %.2fms exceeds maximum %dms",
                averageBurstResponseTime, MAX_AVERAGE_RESPONSE_TIME_MS));
        
        LOG.info("✅ Burst load test completed - Total: {}, Success: {}, Rate: {:.2f}%, " +
                "Avg Response: {:.2f}ms, Throughput: {:.2f} req/sec", 
                totalBurstReq, successBurstReq, burstSuccessRate * 100, 
                averageBurstResponseTime, throughput);
    }

    @Test
    @Order(5)
    @DisplayName("Cross-Service Concurrent Pipeline Test")
    void testCrossServiceConcurrentPipeline() throws Exception {
        LOG.info("Starting cross-service concurrent pipeline test");
        
        int pipelineThreads = 20;
        CountDownLatch pipelineLatch = new CountDownLatch(pipelineThreads);
        AtomicInteger successfulPipelines = new AtomicInteger();
        AtomicLong totalPipelineTime = new AtomicLong();
        
        for (int i = 0; i < pipelineThreads; i++) {
            final int pipelineId = i;
            testExecutorService.submit(() -> {
                try {
                    long pipelineStart = System.currentTimeMillis();
                    
                    // Create test document
                    String content = "Cross-service pipeline test document " + pipelineId + ". " +
                                   "This document will flow through multiple services concurrently. ".repeat(100);
                    
                    PipeDoc originalDoc = createDocumentWithContent("pipeline-" + pipelineId, content);
                    
                    // Step 1: Tika Processing
                    ProcessRequest tikaRequest = createProcessRequest("concurrent-pipeline", "tika-step", originalDoc);
                    ProcessResponse tikaResponse = tikaClient.processData(tikaRequest);
                    assertTrue(tikaResponse.getSuccess(), "Tika step should succeed in concurrent pipeline");
                    
                    // Step 2: Chunker Processing
                    ProcessRequest chunkerRequest = createProcessRequest("concurrent-pipeline", "chunker-step", tikaResponse.getOutputDoc());
                    ProcessResponse chunkerResponse = chunkerClient.processData(chunkerRequest);
                    assertTrue(chunkerResponse.getSuccess(), "Chunker step should succeed in concurrent pipeline");
                    
                    // Step 3: Embedder Processing
                    ProcessRequest embedderRequest = createEmbedderProcessRequest("concurrent-pipeline", "embedder-step", chunkerResponse.getOutputDoc());
                    ProcessResponse embedderResponse = embedderClient.processData(embedderRequest);
                    assertTrue(embedderResponse.getSuccess(), "Embedder step should succeed in concurrent pipeline");
                    
                    // Step 4: Echo Processing (for validation)
                    ProcessRequest echoRequest = createProcessRequest("concurrent-pipeline", "echo-step", embedderResponse.getOutputDoc());
                    ProcessResponse echoResponse = echoClient.processData(echoRequest);
                    assertTrue(echoResponse.getSuccess(), "Echo step should succeed in concurrent pipeline");
                    
                    long pipelineTime = System.currentTimeMillis() - pipelineStart;
                    totalPipelineTime.addAndGet(pipelineTime);
                    successfulPipelines.incrementAndGet();
                    
                    LOG.debug("Pipeline {} completed in {}ms", pipelineId, pipelineTime);
                    
                } catch (Exception e) {
                    LOG.error("Pipeline {} failed: {}", pipelineId, e.getMessage());
                } finally {
                    pipelineLatch.countDown();
                }
            });
        }
        
        // Wait for all pipelines to complete
        assertTrue(pipelineLatch.await(5, TimeUnit.MINUTES),
            "All concurrent pipelines should complete within 5 minutes");
        
        int successful = successfulPipelines.get();
        double pipelineSuccessRate = (double) successful / pipelineThreads;
        double averagePipelineTime = (double) totalPipelineTime.get() / successful;
        
        assertTrue(pipelineSuccessRate >= MIN_SUCCESS_RATE,
            String.format("Pipeline success rate %.2f%% below minimum %.2f%%",
                pipelineSuccessRate * 100, MIN_SUCCESS_RATE * 100));
        
        assertTrue(averagePipelineTime <= 60000, // 1 minute per pipeline
            String.format("Average pipeline time %.2fms excessive", averagePipelineTime));
        
        LOG.info("✅ Cross-service concurrent pipeline test completed - Success: {}/{} ({:.2f}%), " +
                "Avg Time: {:.2f}ms", 
                successful, pipelineThreads, pipelineSuccessRate * 100, averagePipelineTime);
    }

    @Test
    @Order(6)
    @DisplayName("Resource Contention and Deadlock Prevention Test")
    void testResourceContentionHandling() throws Exception {
        LOG.info("Starting resource contention and deadlock prevention test");
        
        int contentionThreads = 30;
        CountDownLatch contentionLatch = new CountDownLatch(contentionThreads);
        AtomicInteger completedOperations = new AtomicInteger();
        AtomicInteger timeoutOperations = new AtomicInteger();
        Map<String, AtomicInteger> serviceCompletions = new ConcurrentHashMap<>();
        
        serviceCompletions.put("tika", new AtomicInteger());
        serviceCompletions.put("chunker", new AtomicInteger());
        serviceCompletions.put("embedder", new AtomicInteger());
        serviceCompletions.put("echo", new AtomicInteger());
        
        // Create competing threads that access the same resources
        for (int i = 0; i < contentionThreads; i++) {
            final int threadId = i;
            testExecutorService.submit(() -> {
                try {
                    // Each thread performs operations on all services with same document IDs
                    // This creates resource contention scenarios
                    String sharedResourceId = "shared-resource-" + (threadId % 5); // 5 shared resources
                    
                    for (String serviceType : Arrays.asList("tika", "chunker", "embedder", "echo")) {
                        try {
                            ProcessRequest request = createTestRequest(serviceType, sharedResourceId + "-" + threadId);
                            PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client = getClientForService(serviceType);
                            
                            // Set timeout to detect potential deadlocks
                            ProcessResponse response = client.withDeadlineAfter(10, TimeUnit.SECONDS)
                                    .processData(request);
                            
                            if (response.getSuccess()) {
                                completedOperations.incrementAndGet();
                                serviceCompletions.get(serviceType).incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            if (e.getMessage().contains("DEADLINE_EXCEEDED")) {
                                timeoutOperations.incrementAndGet();
                                LOG.warn("Timeout detected in {} service for thread {}", serviceType, threadId);
                            }
                            LOG.debug("Resource contention operation failed: {}", e.getMessage());
                        }
                    }
                } finally {
                    contentionLatch.countDown();
                }
            });
        }
        
        // Wait for completion
        assertTrue(contentionLatch.await(3, TimeUnit.MINUTES),
            "Resource contention test should complete within 3 minutes");
        
        int completed = completedOperations.get();
        int timeouts = timeoutOperations.get();
        int totalExpected = contentionThreads * 4; // 4 services per thread
        
        // Verify no deadlocks occurred (all threads completed)
        assertTrue(completed > 0, "Should have completed some operations despite contention");
        
        // Verify timeout handling (some timeouts are acceptable under contention)
        assertTrue(timeouts < totalExpected * 0.3, // Less than 30% timeouts
            String.format("Too many timeouts: %d/%d (%.2f%%)", timeouts, totalExpected, 
                (double) timeouts / totalExpected * 100));
        
        LOG.info("✅ Resource contention test completed - Completed: {}, Timeouts: {}, " +
                "Service distribution: {}", 
                completed, timeouts, serviceCompletions);
    }

    // Helper Methods

    private ConcurrencyTestResult testServiceConcurrency(PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client,
                                                        String serviceType, int threadCount, int requestsPerThread) throws Exception {
        CountDownLatch concurrencyLatch = new CountDownLatch(threadCount);
        AtomicInteger totalRequests = new AtomicInteger();
        AtomicInteger successfulRequests = new AtomicInteger();
        AtomicInteger timeoutCount = new AtomicInteger();
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            testExecutorService.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            long requestStart = System.currentTimeMillis();
                            
                            ProcessRequest request = createTestRequest(serviceType, 
                                    "concurrency-" + serviceType + "-" + threadId + "-" + j);
                            
                            ProcessResponse response = client.withDeadlineAfter(30, TimeUnit.SECONDS)
                                    .processData(request);
                            
                            long responseTime = System.currentTimeMillis() - requestStart;
                            responseTimes.add(responseTime);
                            totalRequests.incrementAndGet();
                            
                            if (response.getSuccess()) {
                                successfulRequests.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            totalRequests.incrementAndGet();
                            if (e.getMessage().contains("DEADLINE_EXCEEDED")) {
                                timeoutCount.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    concurrencyLatch.countDown();
                }
            });
        }
        
        assertTrue(concurrencyLatch.await(5, TimeUnit.MINUTES),
            "Concurrency test should complete within 5 minutes");
        
        // Calculate statistics
        int total = totalRequests.get();
        int successful = successfulRequests.get();
        int timeouts = timeoutCount.get();
        double successRate = total > 0 ? (double) successful / total : 0;
        
        long averageResponseTime = 0;
        long p99ResponseTime = 0;
        
        if (!responseTimes.isEmpty()) {
            responseTimes.sort(Long::compareTo);
            averageResponseTime = (long) responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            int p99Index = (int) Math.ceil(responseTimes.size() * 0.99) - 1;
            p99Index = Math.max(0, Math.min(p99Index, responseTimes.size() - 1));
            p99ResponseTime = responseTimes.get(p99Index);
        }
        
        return new ConcurrencyTestResult(total, successful, successRate, averageResponseTime, 
                p99ResponseTime, timeouts);
    }

    private ProcessRequest createTestRequest(String serviceType, String testId) {
        String content = generateTestContent(serviceType, 500); // 500 words
        
        PipeDoc document;
        if ("tika".equals(serviceType)) {
            // Create document with blob for Tika
            byte[] blobData = content.getBytes(StandardCharsets.UTF_8);
            Blob blob = Blob.newBuilder()
                    .setBlobId("concurrency-blob-" + testId)
                    .setFilename(testId + ".txt")
                    .setData(ByteString.copyFrom(blobData))
                    .setMimeType("text/plain")
                    .build();
            
            document = PipeDoc.newBuilder()
                    .setId(testId)
                    .setTitle("Concurrency Test Document")
                    .setBlob(blob)
                    .build();
        } else {
            document = PipeDoc.newBuilder()
                    .setId(testId)
                    .setTitle("Concurrency Test Document")
                    .setBody(content)
                    .build();
        }
        
        return createProcessRequest("concurrency-test-pipeline", serviceType + "-step", document);
    }

    private ProcessRequest createEmbedderProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("concurrency-stream-" + System.currentTimeMillis())
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

    private ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("concurrency-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder().build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }

    private PipeDoc createDocumentWithContent(String id, String content) {
        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle("Concurrent Pipeline Test Document")
                .setBody(content)
                .build();
    }

    private String generateTestContent(String serviceType, int wordCount) {
        StringBuilder sb = new StringBuilder();
        String[] words = {"concurrency", "test", "content", "stress", "performance", "thread", "safety", 
                         "resource", "contention", "scalability", serviceType, "processing"};
        
        for (int i = 0; i < wordCount; i++) {
            sb.append(words[i % words.length]).append(" ");
            if ((i + 1) % 20 == 0) {
                sb.append(". ");
            }
            if ((i + 1) % 100 == 0) {
                sb.append("\n\n");
            }
        }
        
        return sb.toString();
    }

    private String getServiceTypeForThread(int threadId) {
        String[] services = {"tika", "chunker", "embedder", "echo"};
        return services[threadId % services.length];
    }

    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub getClientForService(String serviceType) {
        switch (serviceType) {
            case "tika": return tikaClient;
            case "chunker": return chunkerClient;
            case "embedder": return embedderClient;
            case "echo": return echoClient;
            default: throw new IllegalArgumentException("Unknown service type: " + serviceType);
        }
    }

    // Inner class for test results
    private static class ConcurrencyTestResult {
        final int totalRequests;
        final int completedRequests;
        final double successRate;
        final long averageResponseTime;
        final long p99ResponseTime;
        final int timeoutCount;
        
        ConcurrencyTestResult(int totalRequests, int completedRequests, double successRate,
                             long averageResponseTime, long p99ResponseTime, int timeoutCount) {
            this.totalRequests = totalRequests;
            this.completedRequests = completedRequests;
            this.successRate = successRate;
            this.averageResponseTime = averageResponseTime;
            this.p99ResponseTime = p99ResponseTime;
            this.timeoutCount = timeoutCount;
        }
    }
}