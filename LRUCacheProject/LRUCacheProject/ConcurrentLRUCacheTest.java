package LRUCacheProject;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Comprehensive test suite for ConcurrentLRUCache
 * 
 * Tests cover:
 * 1. Basic functionality (get, put, eviction)
 * 2. Edge cases (null handling, capacity limits, empty cache)
 * 3. Thread safety under high contention
 * 4. Performance benchmarks
 * 
 * @author [Your Name]
 */
public class ConcurrentLRUCacheTest {

    // ========== BASIC FUNCTIONALITY TESTS ==========

    @Test
    @DisplayName("Should store and retrieve values correctly")
    void testBasicPutAndGet() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(3);
        
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        
        assertEquals("one", cache.get(1));
        assertEquals("two", cache.get(2));
        assertEquals("three", cache.get(3));
        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("Should update value when key already exists")
    void testUpdateExistingKey() {
        ConcurrentLRUCache<String, Integer> cache = new ConcurrentLRUCache<>(2);
        
        cache.put("key1", 100);
        cache.put("key1", 200);  // update
        
        assertEquals(200, cache.get("key1"));
        assertEquals(1, cache.size(), "Size should not increase on update");
    }

    @Test
    @DisplayName("Should return null for non-existent keys")
    void testGetNonExistentKey() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(5);
        assertNull(cache.get(999));
    }

    // ========== EVICTION POLICY TESTS ==========

    @Test
    @DisplayName("Should evict least recently used item when capacity exceeded")
    void testLRUEviction() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(2);
        
        cache.put(1, "A");
        cache.put(2, "B");
        cache.get(1);  // Access 1, making 2 the LRU
        cache.put(3, "C");  // Should evict 2
        
        assertNull(cache.get(2), "Key 2 should have been evicted");
        assertEquals("A", cache.get(1));
        assertEquals("C", cache.get(3));
    }

    @Test
    @DisplayName("Should handle eviction with capacity of 1")
    void testCapacityOne() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(1);
        
        cache.put(1, "first");
        assertEquals("first", cache.get(1));
        
        cache.put(2, "second");  // Should evict 1
        assertNull(cache.get(1));
        assertEquals("second", cache.get(2));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("Should maintain correct order after multiple accesses")
    void testRecencyTracking() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(3);
        
        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");
        
        // Access order: 1, 2, 3 (3 is MRU, 1 is LRU)
        cache.get(1);  // 1 becomes MRU, 2 is now LRU
        cache.get(2);  // 2 becomes MRU, 3 is now LRU
        
        cache.put(4, "D");  // Should evict 3
        
        assertNull(cache.get(3));
        assertNotNull(cache.get(1));
        assertNotNull(cache.get(2));
        assertNotNull(cache.get(4));
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    @DisplayName("Should throw exception for invalid capacity")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ConcurrentLRUCache<Integer, String>(0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new ConcurrentLRUCache<Integer, String>(-5);
        });
    }

    @Test
    @DisplayName("Should handle null key in get gracefully")
    void testNullKeyInGet() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(5);
        assertNull(cache.get(null));
    }

    @Test
    @DisplayName("Should throw exception for null key in put")
    void testNullKeyInPut() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(5);
        assertThrows(NullPointerException.class, () -> {
            cache.put(null, "value");
        });
    }

    @Test
    @DisplayName("Should throw exception for null value in put")
    void testNullValueInPut() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(5);
        assertThrows(NullPointerException.class, () -> {
            cache.put(1, null);
        });
    }

    @Test
    @DisplayName("Clear should remove all entries")
    void testClear() {
        ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(3);
        
        cache.put("a", "apple");
        cache.put("b", "banana");
        cache.put("c", "cherry");
        
        cache.clear();
        
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
    }

    @Test
    @DisplayName("ContainsKey should work correctly")
    void testContainsKey() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(3);
        
        cache.put(1, "one");
        assertTrue(cache.containsKey(1));
        assertFalse(cache.containsKey(2));
    }

    // ========== CONCURRENCY TESTS ==========

    @Test
    @DisplayName("Should handle concurrent puts without data loss")
    void testConcurrentPuts() throws InterruptedException {
        int threads = 10;
        int opsPerThread = 1000;
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(500);
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = threadId * opsPerThread + i;
                        cache.put(key, "value_" + key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test timed out");
        executor.shutdown();
        
        assertTrue(cache.size() <= 500, "Cache size should not exceed capacity");
        assertTrue(cache.size() > 0, "Cache should contain entries");
    }

    @Test
    @DisplayName("Should handle concurrent get and put operations")
    void testConcurrentGetAndPut() throws InterruptedException {
        int threads = 8;
        int operations = 5000;
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(100);
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successfulGets = new AtomicInteger(0);
        
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    Random random = new Random();
                    for (int i = 0; i < operations; i++) {
                        int key = random.nextInt(200);
                        
                        if (random.nextBoolean()) {
                            cache.put(key, "val_" + key);
                        } else {
                            String val = cache.get(key);
                            if (val != null) {
                                successfulGets.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Test timed out");
        executor.shutdown();
        
        assertTrue(cache.size() <= 100, "Cache should respect capacity");
        System.out.println("Successful concurrent gets: " + successfulGets.get());
    }

    @Test
    @DisplayName("Should maintain correct eviction under high contention")
    void testEvictionUnderContention() throws InterruptedException {
        int capacity = 50;
        int threads = 10;
        int opsPerThread = 2000;
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(capacity);
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    Random random = new Random();
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = random.nextInt(150);  // More keys than capacity
                        cache.put(key, "thread_value_" + key);
                        cache.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(20, TimeUnit.SECONDS), "Test timed out");
        executor.shutdown();
        
        assertTrue(cache.size() <= capacity, 
            "Cache size " + cache.size() + " should not exceed capacity " + capacity);
    }

    @Test
    @DisplayName("Should handle concurrent updates to same key")
    void testConcurrentUpdatesSameKey() throws InterruptedException {
        int threads = 20;
        int updatesPerThread = 100;
        ConcurrentLRUCache<String, Integer> cache = new ConcurrentLRUCache<>(10);
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < updatesPerThread; i++) {
                        cache.put("shared_key", threadId * 1000 + i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test timed out");
        executor.shutdown();
        
        assertNotNull(cache.get("shared_key"), "Key should exist after concurrent updates");
        assertEquals(1, cache.size(), "Should only have one entry for the shared key");
    }

    // ========== PERFORMANCE BENCHMARKS ==========

    @Test
    @DisplayName("Performance: Sequential operations baseline")
    void testSequentialPerformance() {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(5000);
        int operations = 100_000;
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < operations; i++) {
            cache.put(i, "value_" + i);
            cache.get(i);
        }
        
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        double opsPerMs = operations / (double) elapsedMs;
        
        System.out.println("Sequential: " + operations + " ops in " + elapsedMs + " ms");
        System.out.println("Throughput: " + String.format("%.2f", opsPerMs * 1000) + " ops/sec");
        
        assertTrue(elapsedMs > 0, "Should complete in measurable time");
    }

    @Test
    @DisplayName("Performance: Concurrent throughput test")
    void testConcurrentThroughput() throws InterruptedException {
        int threads = 8;
        int opsPerThread = 50_000;
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(10_000);
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        long startTime = System.nanoTime();
        
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    Random random = new Random();
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = random.nextInt(20_000);
                        cache.put(key, "val_" + threadId + "_" + key);
                        cache.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Test timed out");
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        
        executor.shutdown();
        
        int totalOps = threads * opsPerThread * 2;  // each iteration does put + get
        double opsPerSec = (totalOps / (double) elapsedMs) * 1000;
        
        System.out.println("\nConcurrent (" + threads + " threads):");
        System.out.println("Total operations: " + totalOps);
        System.out.println("Time: " + elapsedMs + " ms");
        System.out.println("Throughput: " + String.format("%.2f", opsPerSec) + " ops/sec");
        
        assertTrue(elapsedMs > 0, "Should complete in measurable time");
    }

    @Test
    @DisplayName("Performance: Read-heavy workload")
    void testReadHeavyWorkload() throws InterruptedException {
        int capacity = 1000;
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(capacity);
        
        // Pre-populate cache
        for (int i = 0; i < capacity; i++) {
            cache.put(i, "value_" + i);
        }
        
        int threads = 8;
        int readsPerThread = 100_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        long startTime = System.nanoTime();
        
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    Random random = new Random();
                    for (int i = 0; i < readsPerThread; i++) {
                        int key = random.nextInt(capacity);
                        cache.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(20, TimeUnit.SECONDS), "Test timed out");
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        
        executor.shutdown();
        
        int totalReads = threads * readsPerThread;
        double readsPerSec = (totalReads / (double) elapsedMs) * 1000;
        
        System.out.println("\nRead-heavy workload:");
        System.out.println("Total reads: " + totalReads);
        System.out.println("Time: " + elapsedMs + " ms");
        System.out.println("Throughput: " + String.format("%.2f", readsPerSec) + " reads/sec");
    }
}