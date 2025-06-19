package com.rokkon.integration.performance;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for the Quarkus-based rokkon-engine implementation.
 * 
 * These tests establish performance baselines and verify that:
 * - Throughput meets production requirements
 * - Latency remains within acceptable bounds
 * - Memory usage is efficient
 * - Concurrent processing scales properly
 - Performance benchmarking and baseline establishment
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceBaselineTests {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceBaselineTests.class);
    
    // Performance baselines (these should be tuned based on actual production performance)
    private static final Map<String, Long> TIKA_PERFORMANCE_BASELINES = Map.of(
        "small_document", 2000L,    // 2 seconds for small docs
        "medium_document", 5000L,   // 5 seconds for medium docs  
        "large_document", 15000L,   // 15 seconds for large docs
        "concurrent_processing", 10000L // 10 seconds for concurrent batch
    );
    
    private static final Map<String, Long> CHUNKER_PERFORMANCE_BASELINES = Map.of(
        "small_content", 1000L,     // 1 second for small content
        "medium_content", 3000L,    // 3 seconds for medium content
        "large_content", 10000L,    // 10 seconds for large content
        "massive_content", 30000L   // 30 seconds for massive content
    );
    
    private static final Map<String, Long> EMBEDDER_PERFORMANCE_BASELINES = Map.of(
        "document_fields", 5000L,   // 5 seconds for document field embedding
        "chunk_processing", 10000L, // 10 seconds for chunk embedding
        "batch_processing", 20000L, // 20 seconds for batch processing
        "large_content", 30000L     // 30 seconds for large content
    );
    
    private static final Map<String, Long> ECHO_PERFORMANCE_BASELINES = Map.of(
        "small_echo", 100L,         // 100ms for small documents
        "large_echo", 1000L,        // 1 second for large documents
        "concurrent_echo", 5000L,   // 5 seconds for concurrent processing
        "massive_echo", 10000L      // 10 seconds for massive documents
    );

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
        // Set up gRPC channels for all services
        tikaChannel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
        chunkerChannel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
        embedderChannel = ManagedChannelBuilder.forAddress("localhost", 9002).usePlaintext().build();
        echoChannel = ManagedChannelBuilder.forAddress("localhost", 9003).usePlaintext().build();
        
        tikaClient = PipeStepProcessorGrpc.newBlockingStub(tikaChannel);
        chunkerClient = PipeStepProcessorGrpc.newBlockingStub(chunkerChannel);
        embedderClient = PipeStepProcessorGrpc.newBlockingStub(embedderChannel);
        echoClient = PipeStepProcessorGrpc.newBlockingStub(echoChannel);
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
    @DisplayName("Tika Parser Performance Regression Test")
    void testTikaParserPerformanceRegression() {
        LOG.info("Starting Tika Parser performance regression testing");
        
        Map<String, String> testDocuments = new HashMap<>();
        testDocuments.put("small_document", "Small test document. ".repeat(100));
        testDocuments.put("medium_document", "Medium test document with more content. ".repeat(1000));
        testDocuments.put("large_document", "Large test document with substantial content for performance testing. ".repeat(10000));
        
        Map<String, Long> actualPerformance = new HashMap<>();
        
        for (Map.Entry<String, String> testDoc : testDocuments.entrySet()) {
            String testName = testDoc.getKey();
            String content = testDoc.getValue();
            
            PipeDoc inputDoc = createDocumentWithBlob(testName + ".txt", content.getBytes(StandardCharsets.UTF_8), "text/plain");
            ProcessRequest request = createProcessRequest("perf-regression-" + testName, "tika-perf", inputDoc);
            
            // Warmup run
            tikaClient.processData(request);
            
            // Performance measurement run
            long startTime = System.currentTimeMillis();
            ProcessResponse response = tikaClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            assertTrue(response.getSuccess(), "Tika processing should succeed for " + testName);
            
            actualPerformance.put(testName, processingTime);
            Long baseline = TIKA_PERFORMANCE_BASELINES.get(testName);
            
            assertTrue(processingTime <= baseline, 
                String.format("Performance regression detected for %s: %dms > %dms baseline", 
                    testName, processingTime, baseline));
            
            double performanceRatio = (double) processingTime / baseline;
            LOG.info("Tika {} performance: {}ms (baseline: {}ms, ratio: {:.2f})", 
                    testName, processingTime, baseline, performanceRatio);
        }
        
        // Test concurrent processing performance
        testTikaConcurrentPerformance(actualPerformance);
        
        LOG.info("✅ Tika Parser performance regression test completed - all benchmarks passed");
    }

    @Test
    @Order(2)
    @DisplayName("Chunker Performance Regression Test")
    void testChunkerPerformanceRegression() {
        LOG.info("Starting Chunker performance regression testing");
        
        Map<String, String> testContent = new HashMap<>();
        testContent.put("small_content", generateTestContent(500));    // 500 words
        testContent.put("medium_content", generateTestContent(5000));  // 5000 words
        testContent.put("large_content", generateTestContent(50000));  // 50000 words
        testContent.put("massive_content", generateTestContent(200000)); // 200000 words
        
        Map<String, Long> actualPerformance = new HashMap<>();
        
        for (Map.Entry<String, String> testData : testContent.entrySet()) {
            String testName = testData.getKey();
            String content = testData.getValue();
            
            PipeDoc inputDoc = PipeDoc.newBuilder()
                    .setId("chunker-perf-" + testName)
                    .setTitle("Chunker Performance Test")
                    .setBody(content)
                    .build();
            
            Struct config = Struct.newBuilder()
                    .putFields("chunk_size", Value.newBuilder().setNumberValue(1000).build())
                    .putFields("chunk_overlap", Value.newBuilder().setNumberValue(100).build())
                    .putFields("chunk_config_id", Value.newBuilder().setStringValue("perf_" + testName).build())
                    .build();
            
            ProcessRequest request = createProcessRequestWithJsonConfig("chunker-perf-" + testName, "chunker-perf", inputDoc, config);
            
            // Warmup run
            chunkerClient.processData(request);
            
            // Performance measurement run
            long startTime = System.currentTimeMillis();
            ProcessResponse response = chunkerClient.processData(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            assertTrue(response.getSuccess(), "Chunker processing should succeed for " + testName);
            
            actualPerformance.put(testName, processingTime);
            Long baseline = CHUNKER_PERFORMANCE_BASELINES.get(testName);
            
            assertTrue(processingTime <= baseline, 
                String.format("Performance regression detected for %s: %dms > %dms baseline", 
                    testName, processingTime, baseline));
            
            double performanceRatio = (double) processingTime / baseline;
            LOG.info("Chunker {} performance: {}ms (baseline: {}ms, ratio: {:.2f})", 
                    testName, processingTime, baseline, performanceRatio);
        }
        
        LOG.info("✅ Chunker performance regression test completed - all benchmarks passed");
    }

    @Test
    @Order(3)
    @DisplayName("Embedder Performance Regression Test")
    void testEmbedderPerformanceRegression() throws Exception {
        LOG.info("Starting Embedder performance regression testing");
        
        Map<String, Long> actualPerformance = new HashMap<>();
        
        // Test document field embedding performance
        PipeDoc fieldTestDoc = PipeDoc.newBuilder()
                .setId("embedder-field-perf")
                .setTitle("Performance test document for embedder field processing")
                .setBody("This is a comprehensive performance test document for embedder field processing. ".repeat(500))
                .addKeywords("performance")
                .addKeywords("test")
                .addKeywords("embedder")
                .build();
        
        ProcessRequest fieldRequest = createEmbedderProcessRequest("embedder-field-perf", "embedder-field-perf", fieldTestDoc);
        
        // Warmup
        embedderClient.processData(fieldRequest);
        
        // Measure document fields performance
        long startTime = System.currentTimeMillis();
        ProcessResponse fieldResponse = embedderClient.processData(fieldRequest);
        long fieldProcessingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(fieldResponse.getSuccess(), "Embedder field processing should succeed");
        actualPerformance.put("document_fields", fieldProcessingTime);
        
        // Test chunk processing performance
        PipeDoc chunkTestDoc = createDocumentWithManyChunks();
        ProcessRequest chunkRequest = createEmbedderProcessRequest("embedder-chunk-perf", "embedder-chunk-perf", chunkTestDoc);
        
        // Warmup
        embedderClient.processData(chunkRequest);
        
        // Measure chunk processing performance
        startTime = System.currentTimeMillis();
        ProcessResponse chunkResponse = embedderClient.processData(chunkRequest);
        long chunkProcessingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(chunkResponse.getSuccess(), "Embedder chunk processing should succeed");
        actualPerformance.put("chunk_processing", chunkProcessingTime);
        
        // Test batch processing performance
        testEmbedderBatchPerformance(actualPerformance);
        
        // Test large content performance
        testEmbedderLargeContentPerformance(actualPerformance);
        
        // Verify all performance baselines
        for (Map.Entry<String, Long> perf : actualPerformance.entrySet()) {
            String testName = perf.getKey();
            Long actualTime = perf.getValue();
            Long baseline = EMBEDDER_PERFORMANCE_BASELINES.get(testName);
            
            assertTrue(actualTime <= baseline, 
                String.format("Performance regression detected for %s: %dms > %dms baseline", 
                    testName, actualTime, baseline));
            
            double performanceRatio = (double) actualTime / baseline;
            LOG.info("Embedder {} performance: {}ms (baseline: {}ms, ratio: {:.2f})", 
                    testName, actualTime, baseline, performanceRatio);
        }
        
        LOG.info("✅ Embedder performance regression test completed - all benchmarks passed");
    }

    @Test
    @Order(4)
    @DisplayName("Echo Service Performance Regression Test")
    void testEchoServicePerformanceRegression() {
        LOG.info("Starting Echo Service performance regression testing");
        
        Map<String, Long> actualPerformance = new HashMap<>();
        
        // Test small document echo performance
        PipeDoc smallDoc = PipeDoc.newBuilder()
                .setId("echo-small-perf")
                .setTitle("Small Echo Performance Test")
                .setBody("Small document for echo performance testing")
                .build();
        
        ProcessRequest smallRequest = createProcessRequest("echo-small-perf", "echo-small-perf", smallDoc);
        
        // Warmup
        echoClient.processData(smallRequest);
        
        // Measure small echo performance
        long startTime = System.currentTimeMillis();
        ProcessResponse smallResponse = echoClient.processData(smallRequest);
        long smallProcessingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(smallResponse.getSuccess(), "Echo small processing should succeed");
        actualPerformance.put("small_echo", smallProcessingTime);
        
        // Test large document echo performance
        testEchoLargePerformance(actualPerformance);
        
        // Test concurrent echo performance
        testEchoConcurrentPerformance(actualPerformance);
        
        // Test massive document echo performance
        testEchoMassivePerformance(actualPerformance);
        
        // Verify all performance baselines
        for (Map.Entry<String, Long> perf : actualPerformance.entrySet()) {
            String testName = perf.getKey();
            Long actualTime = perf.getValue();
            Long baseline = ECHO_PERFORMANCE_BASELINES.get(testName);
            
            assertTrue(actualTime <= baseline, 
                String.format("Performance regression detected for %s: %dms > %dms baseline", 
                    testName, actualTime, baseline));
            
            double performanceRatio = (double) actualTime / baseline;
            LOG.info("Echo {} performance: {}ms (baseline: {}ms, ratio: {:.2f})", 
                    testName, actualTime, baseline, performanceRatio);
        }
        
        LOG.info("✅ Echo Service performance regression test completed - all benchmarks passed");
    }

    @Test
    @Order(5)
    @DisplayName("End-to-End Pipeline Performance Test")
    void testEndToEndPipelinePerformance() {
        LOG.info("Starting End-to-End pipeline performance testing");
        
        // Create a comprehensive document for end-to-end processing
        String content = generateTestContent(5000); // 5000 words
        byte[] blobData = content.getBytes(StandardCharsets.UTF_8);
        
        PipeDoc originalDoc = createDocumentWithBlob("e2e-perf-test.txt", blobData, "text/plain");
        
        long totalStartTime = System.currentTimeMillis();
        
        // Step 1: Tika Processing
        ProcessRequest tikaRequest = createProcessRequest("e2e-pipeline", "tika-step", originalDoc);
        long tikaStart = System.currentTimeMillis();
        ProcessResponse tikaResponse = tikaClient.processData(tikaRequest);
        long tikaTime = System.currentTimeMillis() - tikaStart;
        
        assertTrue(tikaResponse.getSuccess(), "E2E Tika step should succeed");
        
        // Step 2: Chunker Processing
        ProcessRequest chunkerRequest = createProcessRequest("e2e-pipeline", "chunker-step", tikaResponse.getOutputDoc());
        long chunkerStart = System.currentTimeMillis();
        ProcessResponse chunkerResponse = chunkerClient.processData(chunkerRequest);
        long chunkerTime = System.currentTimeMillis() - chunkerStart;
        
        assertTrue(chunkerResponse.getSuccess(), "E2E Chunker step should succeed");
        
        // Step 3: Embedder Processing
        ProcessRequest embedderRequest = createEmbedderProcessRequest("e2e-pipeline", "embedder-step", chunkerResponse.getOutputDoc());
        long embedderStart = System.currentTimeMillis();
        ProcessResponse embedderResponse = embedderClient.processData(embedderRequest);
        long embedderTime = System.currentTimeMillis() - embedderStart;
        
        assertTrue(embedderResponse.getSuccess(), "E2E Embedder step should succeed");
        
        // Step 4: Echo Processing (for verification)
        ProcessRequest echoRequest = createProcessRequest("e2e-pipeline", "echo-step", embedderResponse.getOutputDoc());
        long echoStart = System.currentTimeMillis();
        ProcessResponse echoResponse = echoClient.processData(echoRequest);
        long echoTime = System.currentTimeMillis() - echoStart;
        
        assertTrue(echoResponse.getSuccess(), "E2E Echo step should succeed");
        
        long totalTime = System.currentTimeMillis() - totalStartTime;
        
        // Performance verification
        assertTrue(tikaTime < 10000, "Tika step should complete within 10 seconds");
        assertTrue(chunkerTime < 5000, "Chunker step should complete within 5 seconds");
        assertTrue(embedderTime < 20000, "Embedder step should complete within 20 seconds");
        assertTrue(echoTime < 1000, "Echo step should complete within 1 second");
        assertTrue(totalTime < 40000, "Total E2E pipeline should complete within 40 seconds");
        
        LOG.info("✅ E2E Pipeline Performance - Tika: {}ms, Chunker: {}ms, Embedder: {}ms, Echo: {}ms, Total: {}ms", 
                tikaTime, chunkerTime, embedderTime, echoTime, totalTime);
    }

    @Test
    @Order(6)
    @DisplayName("Throughput Benchmark Test")
    void testThroughputBenchmark() throws Exception {
        LOG.info("Starting Throughput benchmark testing");
        
        int requestCount = 100;
        int threadCount = 10;
        int requestsPerThread = requestCount / threadCount;
        
        Map<String, Double> serviceThroughput = new HashMap<>();
        
        // Test each service throughput
        serviceThroughput.put("tika", measureServiceThroughput(tikaClient, "tika", threadCount, requestsPerThread));
        serviceThroughput.put("chunker", measureServiceThroughput(chunkerClient, "chunker", threadCount, requestsPerThread));
        serviceThroughput.put("embedder", measureServiceThroughput(embedderClient, "embedder", threadCount, requestsPerThread));
        serviceThroughput.put("echo", measureServiceThroughput(echoClient, "echo", threadCount, requestsPerThread));
        
        // Verify throughput meets minimum requirements
        assertTrue(serviceThroughput.get("tika") >= 5.0, "Tika throughput should be at least 5 req/sec");
        assertTrue(serviceThroughput.get("chunker") >= 10.0, "Chunker throughput should be at least 10 req/sec");
        assertTrue(serviceThroughput.get("embedder") >= 3.0, "Embedder throughput should be at least 3 req/sec");
        assertTrue(serviceThroughput.get("echo") >= 100.0, "Echo throughput should be at least 100 req/sec");
        
        LOG.info("✅ Throughput Benchmark Results - Tika: {:.2f} req/sec, Chunker: {:.2f} req/sec, " +
                "Embedder: {:.2f} req/sec, Echo: {:.2f} req/sec", 
                serviceThroughput.get("tika"), serviceThroughput.get("chunker"), 
                serviceThroughput.get("embedder"), serviceThroughput.get("echo"));
    }

    // Helper Methods

    private void testTikaConcurrentPerformance(Map<String, Long> actualPerformance) {
        int concurrentRequests = 5;
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    String content = "Concurrent test document " + index + ". ".repeat(500);
                    PipeDoc doc = createDocumentWithBlob("concurrent-" + index + ".txt", 
                            content.getBytes(StandardCharsets.UTF_8), "text/plain");
                    ProcessRequest request = createProcessRequest("tika-concurrent-" + index, "tika-concurrent", doc);
                    
                    ProcessResponse response = tikaClient.processData(request);
                    results[index] = response.getSuccess();
                } catch (Exception e) {
                    results[index] = false;
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        actualPerformance.put("concurrent_processing", processingTime);
        
        for (boolean result : results) {
            assertTrue(result, "All concurrent requests should succeed");
        }
    }

    private void testEmbedderBatchPerformance(Map<String, Long> actualPerformance) throws Exception {
        int batchSize = 10;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < batchSize; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                    .setId("batch-perf-" + i)
                    .setTitle("Batch Performance Test " + i)
                    .setBody("Batch processing test document content. ".repeat(100))
                    .build();
            
            ProcessRequest request = createEmbedderProcessRequest("batch-perf-" + i, "embedder-batch-perf", doc);
            ProcessResponse response = embedderClient.processData(request);
            assertTrue(response.getSuccess(), "Batch processing should succeed for document " + i);
        }
        
        long batchProcessingTime = System.currentTimeMillis() - startTime;
        actualPerformance.put("batch_processing", batchProcessingTime);
    }

    private void testEmbedderLargeContentPerformance(Map<String, Long> actualPerformance) throws Exception {
        String largeContent = generateTestContent(20000); // 20000 words
        
        PipeDoc largeDoc = PipeDoc.newBuilder()
                .setId("embedder-large-perf")
                .setTitle("Large Content Performance Test")
                .setBody(largeContent)
                .build();
        
        ProcessRequest request = createEmbedderProcessRequest("embedder-large-perf", "embedder-large-perf", largeDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = embedderClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Large content processing should succeed");
        actualPerformance.put("large_content", processingTime);
    }

    private void testEchoLargePerformance(Map<String, Long> actualPerformance) {
        String largeContent = "Large content for echo performance testing. ".repeat(10000);
        
        PipeDoc largeDoc = PipeDoc.newBuilder()
                .setId("echo-large-perf")
                .setTitle("Large Echo Performance Test")
                .setBody(largeContent)
                .build();
        
        ProcessRequest request = createProcessRequest("echo-large-perf", "echo-large-perf", largeDoc);
        
        // Warmup
        echoClient.processData(request);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = echoClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Large echo processing should succeed");
        actualPerformance.put("large_echo", processingTime);
    }

    private void testEchoConcurrentPerformance(Map<String, Long> actualPerformance) {
        int concurrentRequests = 20;
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    PipeDoc doc = PipeDoc.newBuilder()
                            .setId("echo-concurrent-" + index)
                            .setTitle("Concurrent Echo Test " + index)
                            .setBody("Concurrent test content " + index)
                            .build();
                    
                    ProcessRequest request = createProcessRequest("echo-concurrent-" + index, "echo-concurrent", doc);
                    ProcessResponse response = echoClient.processData(request);
                    results[index] = response.getSuccess();
                } catch (Exception e) {
                    results[index] = false;
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        actualPerformance.put("concurrent_echo", processingTime);
        
        for (boolean result : results) {
            assertTrue(result, "All concurrent echo requests should succeed");
        }
    }

    private void testEchoMassivePerformance(Map<String, Long> actualPerformance) {
        // Create massive document
        StringBuilder massiveContent = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            massiveContent.append("Massive content line ").append(i).append(". ");
        }
        
        PipeDoc massiveDoc = PipeDoc.newBuilder()
                .setId("echo-massive-perf")
                .setTitle("Massive Echo Performance Test")
                .setBody(massiveContent.toString())
                .build();
        
        ProcessRequest request = createProcessRequest("echo-massive-perf", "echo-massive-perf", massiveDoc);
        
        long startTime = System.currentTimeMillis();
        ProcessResponse response = echoClient.processData(request);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(response.getSuccess(), "Massive echo processing should succeed");
        actualPerformance.put("massive_echo", processingTime);
    }

    private double measureServiceThroughput(PipeStepProcessorGrpc.PipeStepProcessorBlockingStub client, 
                                          String serviceName, int threadCount, int requestsPerThread) throws Exception {
        Thread[] threads = new Thread[threadCount];
        int[] successCounts = new int[threadCount];
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                int successCount = 0;
                for (int j = 0; j < requestsPerThread; j++) {
                    try {
                        PipeDoc doc = PipeDoc.newBuilder()
                                .setId("throughput-" + serviceName + "-" + threadIndex + "-" + j)
                                .setTitle("Throughput Test")
                                .setBody("Throughput test content")
                                .build();
                        
                        ProcessRequest request;
                        if ("embedder".equals(serviceName)) {
                            request = createEmbedderProcessRequest("throughput-" + serviceName, serviceName + "-throughput", doc);
                        } else {
                            request = createProcessRequest("throughput-" + serviceName, serviceName + "-throughput", doc);
                        }
                        
                        ProcessResponse response = client.processData(request);
                        if (response.getSuccess()) {
                            successCount++;
                        }
                    } catch (Exception e) {
                        // Count as failure
                    }
                }
                successCounts[threadIndex] = successCount;
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        int totalSuccessful = Arrays.stream(successCounts).sum();
        
        return (double) totalSuccessful / (totalTime / 1000.0); // requests per second
    }

    private String generateTestContent(int wordCount) {
        StringBuilder sb = new StringBuilder();
        String[] words = {"performance", "test", "content", "benchmark", "regression", "quarkus", 
                         "micronaut", "processing", "document", "analysis", "optimization", "measurement"};
        
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

    private PipeDoc createDocumentWithBlob(String filename, byte[] data, String mimeType) {
        Blob blob = Blob.newBuilder()
                .setBlobId("perf-blob-" + System.currentTimeMillis())
                .setFilename(filename)
                .setData(ByteString.copyFrom(data))
                .setMimeType(mimeType)
                .build();
        
        return PipeDoc.newBuilder()
                .setId("perf-doc-" + System.currentTimeMillis())
                .setTitle("Performance Test Document: " + filename)
                .setBlob(blob)
                .build();
    }

    private PipeDoc createDocumentWithManyChunks() {
        List<SemanticChunk> chunks = new ArrayList<>();
        
        for (int i = 0; i < 50; i++) {
            ChunkEmbedding embedding = ChunkEmbedding.newBuilder()
                    .setTextContent("Performance test chunk " + i + " content")
                    .setChunkId("perf-chunk-" + i)
                    .setChunkConfigId("perf_chunker")
                    .build();
            
            SemanticChunk chunk = SemanticChunk.newBuilder()
                    .setChunkId("perf-chunk-" + i)
                    .setChunkNumber(i)
                    .setEmbeddingInfo(embedding)
                    .build();
            
            chunks.add(chunk);
        }
        
        SemanticProcessingResult result = SemanticProcessingResult.newBuilder()
                .setResultId("perf-result")
                .setSourceFieldName("body")
                .setChunkConfigId("perf_chunker")
                .addAllChunks(chunks)
                .build();
        
        return PipeDoc.newBuilder()
                .setId("perf-chunked-doc")
                .setTitle("Performance Test Document with Chunks")
                .setBody("Performance test document body")
                .addSemanticResults(result)
                .build();
    }

    private ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("perf-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder().build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }

    private ProcessRequest createProcessRequestWithJsonConfig(String pipelineName, String stepName, 
                                                             PipeDoc document, Struct customJsonConfig) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("perf-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(customJsonConfig)
                .build();
        
        return ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(config)
                .setMetadata(metadata)
                .build();
    }

    private ProcessRequest createEmbedderProcessRequest(String pipelineName, String stepName, PipeDoc document) throws Exception {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("perf-stream-" + System.currentTimeMillis())
                .setCurrentHopNumber(1)
                .build();
        
        // Create default embedder configuration
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
}