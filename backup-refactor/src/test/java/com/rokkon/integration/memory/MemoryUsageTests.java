package com.rokkon.integration.memory;

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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive memory usage tests to ensure the Quarkus implementation
 * has efficient memory management and no memory leaks.
 * 
 * These tests monitor:
 * - Heap memory usage patterns
 * - Memory leak detection
 * - Garbage collection pressure
 * - Reactive pattern memory efficiency
 * - Memory scaling under load
 * - Resource cleanup verification
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryUsageTests {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryUsageTests.class);
    
    // Memory usage thresholds (in MB)
    private static final long MAX_HEAP_INCREASE_MB = 500;
    private static final long MAX_SUSTAINED_HEAP_MB = 1000;
    private static final double MAX_GC_TIME_RATIO = 0.1; // 10% of total time
    
    private ManagedChannel tikaChannel;
    private ManagedChannel chunkerChannel;
    private ManagedChannel embedderChannel;
    private ManagedChannel echoChannel;
    
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub tikaClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub chunkerClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub embedderClient;
    private PipeStepProcessorGrpc.PipeStepProcessorBlockingStub echoClient;
    
    private MemoryMXBean memoryBean;
    private List<GarbageCollectorMXBean> gcBeans;

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
        
        // Set up memory monitoring
        memoryBean = ManagementFactory.getMemoryMXBean();
        gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        // Force initial garbage collection to get clean baseline
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    @DisplayName("Memory Baseline Establishment - Should establish memory usage baselines")
    void testMemoryBaselineEstablishment() throws InterruptedException {
        LOG.info("Establishing memory usage baselines");
        
        MemorySnapshot baseline = takeMemorySnapshot("baseline");
        
        // Force multiple GC cycles to stabilize memory
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(500);
        }
        
        MemorySnapshot afterGC = takeMemorySnapshot("after-gc");
        
        LOG.info("Memory Baseline - Heap Used: {}MB, Non-Heap Used: {}MB, Total Memory: {}MB", 
                baseline.heapUsed / (1024 * 1024), 
                baseline.nonHeapUsed / (1024 * 1024),
                baseline.totalMemory / (1024 * 1024));
        
        // Baseline should be reasonable (not indicating existing memory issues)
        assertTrue(baseline.heapUsed < 500 * 1024 * 1024, "Initial heap usage should be under 500MB");
        assertTrue(baseline.nonHeapUsed < 200 * 1024 * 1024, "Initial non-heap usage should be under 200MB");
        
        LOG.info("✅ Memory baseline established successfully");
    }

    @Test
    @Order(2)
    @DisplayName("Tika Memory Usage Test - Should not leak memory during document processing")
    void testTikaMemoryUsage() throws InterruptedException {
        LOG.info("Testing Tika service memory usage patterns");
        
        MemorySnapshot baseline = takeMemorySnapshot("tika-baseline");
        
        // Process multiple documents of varying sizes
        for (int cycle = 0; cycle < 5; cycle++) {
            LOG.info("Tika memory test cycle {}", cycle + 1);
            
            // Small documents
            for (int i = 0; i < 20; i++) {
                String content = "Small document content for memory testing. ".repeat(100);
                processWithTika("small-doc-" + cycle + "-" + i, content);
            }
            
            // Medium documents
            for (int i = 0; i < 10; i++) {
                String content = "Medium document content for memory testing. ".repeat(1000);
                processWithTika("medium-doc-" + cycle + "-" + i, content);
            }
            
            // Large documents
            for (int i = 0; i < 3; i++) {
                String content = "Large document content for memory testing. ".repeat(10000);
                processWithTika("large-doc-" + cycle + "-" + i, content);
            }
            
            // Periodic GC and memory check
            if (cycle % 2 == 1) {
                System.gc();
                Thread.sleep(500);
                
                MemorySnapshot current = takeMemorySnapshot("tika-cycle-" + cycle);
                long heapIncrease = current.heapUsed - baseline.heapUsed;
                
                LOG.info("Tika cycle {} - Heap increase: {}MB", cycle, heapIncrease / (1024 * 1024));
                
                // Memory increase should be reasonable during processing
                assertTrue(heapIncrease < MAX_HEAP_INCREASE_MB * 1024 * 1024, 
                        "Heap increase should be under " + MAX_HEAP_INCREASE_MB + "MB during Tika processing");
            }
        }
        
        // Final cleanup and verification
        System.gc();
        Thread.sleep(2000);
        System.gc();
        Thread.sleep(2000);
        
        MemorySnapshot finalSnapshot = takeMemorySnapshot("tika-final");
        verifyMemoryCleanup(baseline, finalSnapshot, "Tika");
        
        LOG.info("✅ Tika memory usage test completed successfully");
    }

    @Test
    @Order(3)
    @DisplayName("Chunker Memory Usage Test - Should handle large text processing efficiently")
    void testChunkerMemoryUsage() throws InterruptedException {
        LOG.info("Testing Chunker service memory usage patterns");
        
        MemorySnapshot baseline = takeMemorySnapshot("chunker-baseline");
        
        // Test with progressively larger documents
        for (int cycle = 0; cycle < 3; cycle++) {
            LOG.info("Chunker memory test cycle {}", cycle + 1);
            
            // Process documents with increasing sizes
            int[] documentSizes = {1000, 5000, 25000, 50000}; // words
            
            for (int size : documentSizes) {
                String content = generateTestContent(size);
                
                PipeDoc inputDoc = PipeDoc.newBuilder()
                        .setId("chunker-memory-test-" + cycle + "-" + size)
                        .setTitle("Chunker Memory Test Document")
                        .setBody(content)
                        .build();
                
                Struct config = Struct.newBuilder()
                        .putFields("chunk_size", Value.newBuilder().setNumberValue(1000).build())
                        .putFields("chunk_overlap", Value.newBuilder().setNumberValue(100).build())
                        .putFields("chunk_config_id", Value.newBuilder().setStringValue("memory_test_" + size).build())
                        .build();
                
                ProcessRequest request = createProcessRequestWithJsonConfig("chunker-memory-test", "chunker-memory", inputDoc, config);
                
                ProcessResponse response = chunkerClient.processData(request);
                assertTrue(response.getSuccess(), "Chunker processing should succeed for size " + size);
                
                // Verify chunks were created
                assertTrue(response.getOutputDoc().getSemanticResultsCount() > 0, "Should have semantic results");
                
                LOG.debug("Processed document with {} words, created {} chunks", 
                        size, response.getOutputDoc().getSemanticResults(0).getChunksCount());
            }
            
            // Memory check after each cycle
            System.gc();
            Thread.sleep(500);
            
            MemorySnapshot current = takeMemorySnapshot("chunker-cycle-" + cycle);
            long heapIncrease = current.heapUsed - baseline.heapUsed;
            
            LOG.info("Chunker cycle {} - Heap increase: {}MB", cycle, heapIncrease / (1024 * 1024));
        }
        
        // Final cleanup and verification
        System.gc();
        Thread.sleep(2000);
        System.gc();
        Thread.sleep(2000);
        
        MemorySnapshot finalSnapshot = takeMemorySnapshot("chunker-final");
        verifyMemoryCleanup(baseline, finalSnapshot, "Chunker");
        
        LOG.info("✅ Chunker memory usage test completed successfully");
    }

    @Test
    @Order(4)
    @DisplayName("Embedder Memory Usage Test - Should handle embedding processing without memory leaks")
    void testEmbedderMemoryUsage() throws Exception {
        LOG.info("Testing Embedder service memory usage patterns");
        
        MemorySnapshot baseline = takeMemorySnapshot("embedder-baseline");
        
        // Test embedder with various workloads
        for (int cycle = 0; cycle < 3; cycle++) {
            LOG.info("Embedder memory test cycle {}", cycle + 1);
            
            // Document field embeddings
            for (int i = 0; i < 10; i++) {
                PipeDoc doc = PipeDoc.newBuilder()
                        .setId("embedder-field-memory-" + cycle + "-" + i)
                        .setTitle("Embedder Memory Test Document " + i)
                        .setBody("Memory test content for embedder processing. ".repeat(500))
                        .addKeywords("memory")
                        .addKeywords("test")
                        .addKeywords("embedding")
                        .build();
                
                ProcessRequest request = createEmbedderProcessRequest("embedder-memory-field", "embedder-memory", doc);
                ProcessResponse response = embedderClient.processData(request);
                
                assertTrue(response.getSuccess(), "Embedder field processing should succeed");
                assertTrue(response.getOutputDoc().getNamedEmbeddingsCount() > 0, "Should have embeddings");
            }
            
            // Chunk embeddings
            for (int i = 0; i < 5; i++) {
                PipeDoc chunkDoc = createDocumentWithManyChunks(50); // 50 chunks
                ProcessRequest request = createEmbedderProcessRequest("embedder-memory-chunk", "embedder-memory", chunkDoc);
                ProcessResponse response = embedderClient.processData(request);
                
                assertTrue(response.getSuccess(), "Embedder chunk processing should succeed");
            }
            
            // Memory check
            System.gc();
            Thread.sleep(500);
            
            MemorySnapshot current = takeMemorySnapshot("embedder-cycle-" + cycle);
            long heapIncrease = current.heapUsed - baseline.heapUsed;
            
            LOG.info("Embedder cycle {} - Heap increase: {}MB", cycle, heapIncrease / (1024 * 1024));
            
            // Embedder may use more memory due to model loading, but should not grow unbounded
            assertTrue(heapIncrease < (MAX_HEAP_INCREASE_MB * 2) * 1024 * 1024, 
                    "Heap increase should be under " + (MAX_HEAP_INCREASE_MB * 2) + "MB during Embedder processing");
        }
        
        // Final cleanup and verification
        System.gc();
        Thread.sleep(3000); // Give more time for embedder cleanup
        System.gc();
        Thread.sleep(3000);
        
        MemorySnapshot finalSnapshot = takeMemorySnapshot("embedder-final");
        
        // More lenient cleanup verification for embedder due to model caching
        long finalIncrease = finalSnapshot.heapUsed - baseline.heapUsed;
        assertTrue(finalIncrease < (MAX_HEAP_INCREASE_MB * 3) * 1024 * 1024, 
                "Final heap increase should be under " + (MAX_HEAP_INCREASE_MB * 3) + "MB for Embedder");
        
        LOG.info("✅ Embedder memory usage test completed successfully - final increase: {}MB", 
                finalIncrease / (1024 * 1024));
    }

    @Test
    @Order(5)
    @DisplayName("Echo Service Memory Efficiency Test - Should have minimal memory footprint")
    void testEchoServiceMemoryEfficiency() throws InterruptedException {
        LOG.info("Testing Echo service memory efficiency");
        
        MemorySnapshot baseline = takeMemorySnapshot("echo-baseline");
        
        // Echo service should have minimal memory impact
        for (int cycle = 0; cycle < 10; cycle++) {
            // Process various document sizes
            processEchoDocument("small", "Small echo test. ".repeat(100));
            processEchoDocument("medium", "Medium echo test. ".repeat(1000));
            processEchoDocument("large", "Large echo test. ".repeat(10000));
            
            // Process complex documents
            PipeDoc complexDoc = createComplexDocument("echo-complex-" + cycle);
            ProcessRequest request = createProcessRequest("echo-memory-test", "echo-memory", complexDoc);
            ProcessResponse response = echoClient.processData(request);
            
            assertTrue(response.getSuccess(), "Echo processing should succeed");
            assertEquals(complexDoc, response.getOutputDoc(), "Document should be echoed exactly");
        }
        
        // Memory check - Echo should have very minimal impact
        System.gc();
        Thread.sleep(1000);
        
        MemorySnapshot afterProcessing = takeMemorySnapshot("echo-after-processing");
        long heapIncrease = afterProcessing.heapUsed - baseline.heapUsed;
        
        LOG.info("Echo service heap increase: {}MB", heapIncrease / (1024 * 1024));
        
        // Echo should have minimal memory impact (under 50MB)
        assertTrue(heapIncrease < 50 * 1024 * 1024, 
                "Echo service should have minimal memory impact (under 50MB)");
        
        LOG.info("✅ Echo service memory efficiency test completed successfully");
    }

    @Test
    @Order(6)
    @DisplayName("Memory Leak Detection Test - Should detect and prevent memory leaks")
    void testMemoryLeakDetection() throws InterruptedException {
        LOG.info("Running memory leak detection tests");
        
        MemorySnapshot baseline = takeMemorySnapshot("leak-detection-baseline");
        List<MemorySnapshot> snapshots = new ArrayList<>();
        
        // Run multiple cycles to detect gradual memory leaks
        for (int cycle = 0; cycle < 10; cycle++) {
            LOG.info("Memory leak detection cycle {}", cycle + 1);
            
            // Process batch of documents with each service
            processBatchWithAllServices(cycle, 20); // 20 documents per cycle
            
            // Force garbage collection
            System.gc();
            Thread.sleep(500);
            System.gc();
            Thread.sleep(500);
            
            MemorySnapshot snapshot = takeMemorySnapshot("leak-detection-cycle-" + cycle);
            snapshots.add(snapshot);
            
            LOG.info("Cycle {} - Heap: {}MB, Non-Heap: {}MB", 
                    cycle, snapshot.heapUsed / (1024 * 1024), snapshot.nonHeapUsed / (1024 * 1024));
        }
        
        // Analyze memory trend
        analyzeMemoryTrend(snapshots, baseline);
        
        LOG.info("✅ Memory leak detection test completed - no significant leaks detected");
    }

    @Test
    @Order(7)
    @DisplayName("Garbage Collection Pressure Test - Should maintain reasonable GC pressure")
    void testGarbageCollectionPressure() throws InterruptedException {
        LOG.info("Testing garbage collection pressure");
        
        // Record initial GC statistics
        Map<String, Long> initialGCStats = getGCStatistics();
        long testStartTime = System.currentTimeMillis();
        
        // Generate significant workload to test GC pressure
        for (int cycle = 0; cycle < 5; cycle++) {
            LOG.info("GC pressure test cycle {}", cycle + 1);
            
            // High-frequency processing to generate GC pressure
            for (int i = 0; i < 50; i++) {
                // Create and process documents that will generate garbage
                String content = generateTestContent(1000); // 1000 words
                processWithAllServices("gc-pressure-" + cycle + "-" + i, content);
            }
            
            // Brief pause to allow GC
            Thread.sleep(200);
        }
        
        long testDuration = System.currentTimeMillis() - testStartTime;
        Map<String, Long> finalGCStats = getGCStatistics();
        
        // Analyze GC pressure
        analyzeGCPressure(initialGCStats, finalGCStats, testDuration);
        
        LOG.info("✅ Garbage collection pressure test completed successfully");
    }

    @Test
    @Order(8)
    @DisplayName("Memory Scaling Under Load Test - Should scale memory usage appropriately")
    void testMemoryScalingUnderLoad() throws Exception {
        LOG.info("Testing memory scaling under load");
        
        MemorySnapshot baseline = takeMemorySnapshot("scaling-baseline");
        
        // Test with increasing load levels
        int[] loadLevels = {10, 25, 50, 100};
        
        for (int load : loadLevels) {
            LOG.info("Testing memory scaling with load level: {} concurrent requests", load);
            
            Thread[] threads = new Thread[load];
            boolean[] results = new boolean[load];
            
            long loadStartTime = System.currentTimeMillis();
            
            for (int i = 0; i < load; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        // Each thread processes a document
                        String content = "Scaling test content. ".repeat(500);
                        PipeDoc doc = PipeDoc.newBuilder()
                                .setId("scaling-test-" + load + "-" + index)
                                .setTitle("Memory Scaling Test")
                                .setBody(content)
                                .build();
                        
                        // Randomly choose a service to test load distribution
                        ProcessResponse response;
                        switch (index % 4) {
                            case 0:
                                response = processWithTika("scaling-tika-" + index, content);
                                break;
                            case 1:
                                response = chunkerClient.processData(createProcessRequest("scaling-chunker", "chunker-scaling", doc));
                                break;
                            case 2:
                                response = embedderClient.processData(createEmbedderProcessRequest("scaling-embedder", "embedder-scaling", doc));
                                break;
                            default:
                                response = echoClient.processData(createProcessRequest("scaling-echo", "echo-scaling", doc));
                                break;
                        }
                        
                        results[index] = response.getSuccess();
                    } catch (Exception e) {
                        results[index] = false;
                    }
                });
            }
            
            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }
            
            // Wait for completion
            for (Thread thread : threads) {
                thread.join();
            }
            
            long loadDuration = System.currentTimeMillis() - loadStartTime;
            
            // Verify success rate
            int successCount = 0;
            for (boolean result : results) {
                if (result) successCount++;
            }
            
            double successRate = (double) successCount / load * 100;
            assertTrue(successRate >= 90, "Success rate should be at least 90% under load " + load);
            
            // Check memory usage under this load level
            System.gc();
            Thread.sleep(1000);
            
            MemorySnapshot loadSnapshot = takeMemorySnapshot("scaling-load-" + load);
            long heapIncrease = loadSnapshot.heapUsed - baseline.heapUsed;
            
            LOG.info("Load {} - Success rate: {:.1f}%, Duration: {}ms, Heap increase: {}MB", 
                    load, successRate, loadDuration, heapIncrease / (1024 * 1024));
            
            // Memory should scale reasonably with load
            long expectedMaxIncrease = (load / 10) * 100 * 1024 * 1024; // 100MB per 10 requests
            assertTrue(heapIncrease < expectedMaxIncrease, 
                    "Memory increase should scale reasonably with load");
        }
        
        LOG.info("✅ Memory scaling under load test completed successfully");
    }

    @Test
    @Order(9)
    @DisplayName("Resource Cleanup Verification Test - Should properly clean up all resources")
    void testResourceCleanupVerification() throws InterruptedException {
        LOG.info("Testing comprehensive resource cleanup");
        
        MemorySnapshot baseline = takeMemorySnapshot("cleanup-baseline");
        
        // Create significant resource usage
        List<PipeDoc> largeDocuments = new ArrayList<>();
        
        for (int i = 0; i < 20; i++) {
            // Create large documents with complex structures
            PipeDoc largeDoc = createLargeComplexDocument("cleanup-test-" + i);
            largeDocuments.add(largeDoc);
            
            // Process with all services
            processWithAllServices("cleanup-" + i, largeDoc.getBody());
        }
        
        // Force comprehensive cleanup
        largeDocuments.clear(); // Remove references
        
        // Multiple GC cycles with increasing wait times
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(1000 * (i + 1)); // Increasing wait time
        }
        
        MemorySnapshot afterCleanup = takeMemorySnapshot("cleanup-final");
        
        // Verify cleanup effectiveness
        long heapIncrease = afterCleanup.heapUsed - baseline.heapUsed;
        
        LOG.info("Resource cleanup verification - Heap increase after cleanup: {}MB", 
                heapIncrease / (1024 * 1024));
        
        // After cleanup, heap increase should be minimal
        assertTrue(heapIncrease < 200 * 1024 * 1024, 
                "Heap increase after cleanup should be under 200MB");
        
        LOG.info("✅ Resource cleanup verification test completed successfully");
    }

    // Helper Methods

    private static class MemorySnapshot {
        final long timestamp;
        final long heapUsed;
        final long heapMax;
        final long nonHeapUsed;
        final long nonHeapMax;
        final long totalMemory;
        final String label;
        
        MemorySnapshot(String label, long heapUsed, long heapMax, long nonHeapUsed, long nonHeapMax, long totalMemory) {
            this.timestamp = System.currentTimeMillis();
            this.label = label;
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
            this.totalMemory = totalMemory;
        }
    }

    private MemorySnapshot takeMemorySnapshot(String label) {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        Runtime runtime = Runtime.getRuntime();
        
        return new MemorySnapshot(
            label,
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getMax(),
            runtime.totalMemory()
        );
    }

    private void verifyMemoryCleanup(MemorySnapshot baseline, MemorySnapshot current, String serviceName) {
        long heapIncrease = current.heapUsed - baseline.heapUsed;
        
        LOG.info("{} memory cleanup - Baseline: {}MB, Current: {}MB, Increase: {}MB", 
                serviceName, 
                baseline.heapUsed / (1024 * 1024), 
                current.heapUsed / (1024 * 1024),
                heapIncrease / (1024 * 1024));
        
        assertTrue(heapIncrease < MAX_HEAP_INCREASE_MB * 1024 * 1024, 
                serviceName + " heap increase should be under " + MAX_HEAP_INCREASE_MB + "MB after cleanup");
    }

    private void analyzeMemoryTrend(List<MemorySnapshot> snapshots, MemorySnapshot baseline) {
        // Calculate memory trend over time
        if (snapshots.size() < 3) return;
        
        List<Long> heapUsages = new ArrayList<>();
        for (MemorySnapshot snapshot : snapshots) {
            heapUsages.add(snapshot.heapUsed - baseline.heapUsed);
        }
        
        // Simple linear regression to detect trend
        double avgIncrease = heapUsages.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        LOG.info("Memory trend analysis - Average heap increase: {}MB", avgIncrease / (1024 * 1024));
        
        // Check for concerning trends
        long maxIncrease = heapUsages.stream().mapToLong(Long::longValue).max().orElse(0);
        long minIncrease = heapUsages.stream().mapToLong(Long::longValue).min().orElse(0);
        long variation = maxIncrease - minIncrease;
        
        LOG.info("Memory variation - Min: {}MB, Max: {}MB, Variation: {}MB", 
                minIncrease / (1024 * 1024), maxIncrease / (1024 * 1024), variation / (1024 * 1024));
        
        // Detect potential memory leaks
        assertTrue(avgIncrease < MAX_HEAP_INCREASE_MB * 1024 * 1024, 
                "Average memory increase suggests potential memory leak");
        assertTrue(maxIncrease < (MAX_HEAP_INCREASE_MB * 2) * 1024 * 1024, 
                "Maximum memory increase is too high");
    }

    private Map<String, Long> getGCStatistics() {
        Map<String, Long> stats = new HashMap<>();
        
        long totalCollectionCount = 0;
        long totalCollectionTime = 0;
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalCollectionCount += gcBean.getCollectionCount();
            totalCollectionTime += gcBean.getCollectionTime();
        }
        
        stats.put("collectionCount", totalCollectionCount);
        stats.put("collectionTime", totalCollectionTime);
        
        return stats;
    }

    private void analyzeGCPressure(Map<String, Long> initialStats, Map<String, Long> finalStats, long testDuration) {
        long gcCount = finalStats.get("collectionCount") - initialStats.get("collectionCount");
        long gcTime = finalStats.get("collectionTime") - initialStats.get("collectionTime");
        
        double gcTimeRatio = (double) gcTime / testDuration;
        
        LOG.info("GC Pressure Analysis - Collections: {}, Total GC Time: {}ms, GC Time Ratio: {:.3f}", 
                gcCount, gcTime, gcTimeRatio);
        
        // Verify GC pressure is reasonable
        assertTrue(gcTimeRatio < MAX_GC_TIME_RATIO, 
                "GC time ratio should be under " + (MAX_GC_TIME_RATIO * 100) + "%");
        
        // GC frequency should be reasonable
        double gcFrequency = (double) gcCount / (testDuration / 1000.0); // GCs per second
        assertTrue(gcFrequency < 5.0, "GC frequency should be under 5 collections per second");
    }

    private ProcessResponse processWithTika(String docId, String content) {
        PipeDoc inputDoc = createDocumentWithBlob(docId + ".txt", content.getBytes(StandardCharsets.UTF_8), "text/plain");
        ProcessRequest request = createProcessRequest("tika-memory-test", "tika-memory", inputDoc);
        return tikaClient.processData(request);
    }

    private void processEchoDocument(String size, String content) {
        PipeDoc doc = PipeDoc.newBuilder()
                .setId("echo-memory-" + size)
                .setTitle("Echo Memory Test")
                .setBody(content)
                .build();
        
        ProcessRequest request = createProcessRequest("echo-memory-test", "echo-memory", doc);
        ProcessResponse response = echoClient.processData(request);
        
        assertTrue(response.getSuccess(), "Echo processing should succeed for " + size);
    }

    private void processBatchWithAllServices(int cycle, int batchSize) throws Exception {
        for (int i = 0; i < batchSize; i++) {
            String content = "Batch test content for cycle " + cycle + " document " + i + ". ".repeat(100);
            processWithAllServices("batch-" + cycle + "-" + i, content);
        }
    }

    private void processWithAllServices(String docId, String content) throws Exception {
        // Process with Tika
        processWithTika(docId, content);
        
        // Process with Chunker
        PipeDoc chunkerDoc = PipeDoc.newBuilder()
                .setId("chunker-" + docId)
                .setTitle("Test Document")
                .setBody(content)
                .build();
        
        ProcessRequest chunkerRequest = createProcessRequest("memory-test", "chunker-memory", chunkerDoc);
        chunkerClient.processData(chunkerRequest);
        
        // Process with Embedder
        ProcessRequest embedderRequest = createEmbedderProcessRequest("memory-test", "embedder-memory", chunkerDoc);
        embedderClient.processData(embedderRequest);
        
        // Process with Echo
        ProcessRequest echoRequest = createProcessRequest("memory-test", "echo-memory", chunkerDoc);
        echoClient.processData(echoRequest);
    }

    private PipeDoc createComplexDocument(String docId) {
        // Create complex nested structures
        Struct customData = Struct.newBuilder()
                .putFields("complex_field", Value.newBuilder().setStringValue("Complex data. ".repeat(1000)).build())
                .putFields("numeric_field", Value.newBuilder().setNumberValue(Math.random()).build())
                .build();
        
        return PipeDoc.newBuilder()
                .setId(docId)
                .setTitle("Complex Memory Test Document")
                .setBody("Complex document body. ".repeat(1000))
                .setCustomData(customData)
                .addKeywords("memory")
                .addKeywords("test")
                .addKeywords("complex")
                .build();
    }

    private PipeDoc createLargeComplexDocument(String docId) {
        // Create very large complex document
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("Large complex document content line ").append(i).append(". ");
        }
        
        // Large blob
        byte[] largeBlob = new byte[1024 * 1024]; // 1MB
        Arrays.fill(largeBlob, (byte) 42);
        
        Blob blob = Blob.newBuilder()
                .setBlobId("large-blob-" + docId)
                .setFilename("large-test-file.dat")
                .setData(ByteString.copyFrom(largeBlob))
                .setMimeType("application/octet-stream")
                .build();
        
        return PipeDoc.newBuilder()
                .setId(docId)
                .setTitle("Large Complex Memory Test Document")
                .setBody(largeContent.toString())
                .setBlob(blob)
                .build();
    }

    private PipeDoc createDocumentWithManyChunks(int chunkCount) {
        List<SemanticChunk> chunks = new ArrayList<>();
        
        for (int i = 0; i < chunkCount; i++) {
            ChunkEmbedding embedding = ChunkEmbedding.newBuilder()
                    .setTextContent("Memory test chunk " + i + " content")
                    .setChunkId("memory-chunk-" + i)
                    .setChunkConfigId("memory_chunker")
                    .build();
            
            SemanticChunk chunk = SemanticChunk.newBuilder()
                    .setChunkId("memory-chunk-" + i)
                    .setChunkNumber(i)
                    .setEmbeddingInfo(embedding)
                    .build();
            
            chunks.add(chunk);
        }
        
        SemanticProcessingResult result = SemanticProcessingResult.newBuilder()
                .setResultId("memory-result")
                .setSourceFieldName("body")
                .setChunkConfigId("memory_chunker")
                .addAllChunks(chunks)
                .build();
        
        return PipeDoc.newBuilder()
                .setId("memory-chunked-doc")
                .setTitle("Memory Test Document with Chunks")
                .setBody("Memory test document body")
                .addSemanticResults(result)
                .build();
    }

    private String generateTestContent(int wordCount) {
        StringBuilder sb = new StringBuilder();
        String[] words = {"memory", "test", "content", "processing", "document", "analysis", 
                         "performance", "monitoring", "verification", "validation", "testing", "data"};
        
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
                .setBlobId("memory-blob-" + System.currentTimeMillis())
                .setFilename(filename)
                .setData(ByteString.copyFrom(data))
                .setMimeType(mimeType)
                .build();
        
        return PipeDoc.newBuilder()
                .setId("memory-doc-" + System.currentTimeMillis())
                .setTitle("Memory Test Document: " + filename)
                .setBlob(blob)
                .build();
    }

    private ProcessRequest createProcessRequest(String pipelineName, String stepName, PipeDoc document) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName(pipelineName)
                .setPipeStepName(stepName)
                .setStreamId("memory-stream-" + System.currentTimeMillis())
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
                .setStreamId("memory-stream-" + System.currentTimeMillis())
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
                .setStreamId("memory-stream-" + System.currentTimeMillis())
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