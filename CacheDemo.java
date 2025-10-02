
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class CacheDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("Cache project demo.");
        System.out.println("1) Running internal tests...");
        runTests();
        System.out.println("\n2) Running benchmarks (may take a few seconds)...");
        runBenchmarks();
    }

    private static void runTests() {
        int passed = 0, failed = 0;
        try {
            testLRUBasic();
            System.out.println("testLRUBasic: PASS");
            passed++;
        } catch (Throwable t) {
            System.out.println("testLRUBasic: FAIL -> " + t.getMessage());
            failed++;
        }
        try {
            testLFUBasic();
            System.out.println("testLFUBasic: PASS");
            passed++;
        } catch (Throwable t) {
            System.out.println("testLFUBasic: FAIL -> " + t.getMessage());
            failed++;
        }
        try {
            testThreadSafety();
            System.out.println("testThreadSafety: PASS");
            passed++;
        } catch (Throwable t) {
            System.out.println("testThreadSafety: FAIL -> " + t.getMessage());
            failed++;
        }
        try {
            testStats();
            System.out.println("testStats: PASS");
            passed++;
        } catch (Throwable t) {
            System.out.println("testStats: FAIL -> " + t.getMessage());
            failed++;
        }
        try {
            testRemove();
            System.out.println("testRemove: PASS");
            passed++;
        } catch (Throwable t) {
            System.out.println("testRemove: FAIL -> " + t.getMessage());
            failed++;
        }
        try {
            testCapacityOne();
            System.out.println("testCapacityOne: PASS");
            passed++;
        } catch (Throwable t) {
            System.out.println("testCapacityOne: FAIL -> " + t.getMessage());
            failed++;
        }

        System.out.printf("Tests passed: %d, failed: %d%n", passed, failed);
    }

    private static void testLRUBasic() {
        ThreadSafeCache<String, Integer> c = new ThreadSafeCache<>(3, new LRUCachePolicy<>());
        c.put("a", 1);
        c.put("b", 2);
        c.put("c", 3);
        c.get("a");
        c.get("b");
        c.put("d", 4);
        Integer cv = c.get("c");
        if (cv != null)
            throw new RuntimeException("expected 'c' evicted but found: " + cv);
        Integer dv = c.get("d");
        if (dv == null || dv != 4)
            throw new RuntimeException("expected d=4 but got: " + dv);
    }

    private static void testLFUBasic() {
        ThreadSafeCache<String, String> c = new ThreadSafeCache<>(2, new LFUCachePolicy<>());
        c.put("x", "X");
        c.put("y", "Y");
        c.get("x");
        c.get("x");
        c.get("y");
        c.put("z", "Z");
        String yv = c.get("y");
        if (yv != null)
            throw new RuntimeException("expected 'y' evicted but found: " + yv);
        String xv = c.get("x");
        if (!"X".equals(xv))
            throw new RuntimeException("expected x present");
    }

    private static void testThreadSafety() throws InterruptedException {
        final ThreadSafeCache<String, Integer> c = new ThreadSafeCache<>(50, new LRUCachePolicy<>());
        Runnable worker = () -> {
            Random r = new Random();
            for (int i = 0; i < 200; i++) {
                String k = "k" + (i % 100);
                if (r.nextDouble() < 0.5)
                    c.put(k, i);
                else
                    c.get(k);
            }
        };
        Thread[] t = new Thread[8];
        for (int i = 0; i < t.length; i++) {
            t[i] = new Thread(worker);
            t[i].start();
        }
        for (Thread th : t)
            th.join();
        if (c.stats().size > 50)
            throw new RuntimeException("cache size exceeded capacity");
    }

    private static void testStats() {
        ThreadSafeCache<String, String> c = new ThreadSafeCache<>(2, new LRUCachePolicy<>());
        c.put("a", "A");
        c.put("b", "B");
        c.get("a"); // hit
        c.get("x"); // miss
        CacheStats st = c.stats();
        if (st.hits != 1 || st.misses != 1)
            throw new RuntimeException("stats mismatch: " + st);
    }

    private static void testRemove() {
        ThreadSafeCache<String, String> c = new ThreadSafeCache<>(2, new LRUCachePolicy<>());
        c.put("a", "A");
        c.remove("a");
        if (c.get("a") != null)
            throw new RuntimeException("remove failed");
    }

    private static void testCapacityOne() {
        ThreadSafeCache<String, Integer> c = new ThreadSafeCache<>(1, new LRUCachePolicy<>());
        c.put("a", 1);
        c.put("b", 2);
        if (c.get("a") != null)
            throw new RuntimeException("capacity-1 eviction failed");
        if (c.get("b") == null)
            throw new RuntimeException("expected 'b' present");
    }

    private static void runBenchmarks() {
        String[] policies = { "LRU", "LFU" };
        String[] patterns = { "uniform", "skewed", "sequential" };
        for (String p : policies) {
            for (String pattern : patterns) {
                CacheStats stats;
                if ("LRU".equals(p))
                    stats = benchmark(new LRUCachePolicy<>(), pattern, 100, 20000);
                else
                    stats = benchmark(new LFUCachePolicy<>(), pattern, 100, 20000);
                System.out.printf("%3s | pattern=%-9s | hitRate=%.4f | hits=%d misses=%d size=%d%n",
                        p, pattern, stats.hitRate, stats.hits, stats.misses, stats.size);
            }
        }
    }

    private static CacheStats benchmark(EvictionPolicy<String> policy, String pattern, int capacity, int nOps) {
        ThreadSafeCache<String, String> cache = new ThreadSafeCache<>(capacity, policy);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 200; i++)
            keys.add("k" + i);
        List<String> popular = keys.subList(0, 10);
        for (int i = 0; i < capacity; i++)
            cache.put(keys.get(i), "v");
        Random r = new Random();
        for (int i = 0; i < nOps; i++) {
            String k;
            switch (pattern) {
                case "uniform":
                    k = keys.get(r.nextInt(keys.size()));
                    break;
                case "skewed":
                    k = (r.nextDouble() < 0.8) ? popular.get(r.nextInt(popular.size()))
                            : keys.get(r.nextInt(keys.size()));
                    break;
                case "sequential":
                    k = keys.get(i % keys.size());
                    break;
                default:
                    k = keys.get(r.nextInt(keys.size()));
            }
            if (r.nextDouble() < 0.6)
                cache.get(k);
            else
                cache.put(k, "v");
        }
        return cache.stats();
    }

    interface EvictionPolicy<K> {
        void onGet(K key);

        void onPut(K key);

        void onRemove(K key);

        K evict();
    }

    static class LRUCachePolicy<K> implements EvictionPolicy<K> {
        private final LinkedHashMap<K, Boolean> accessOrder = new LinkedHashMap<>(16, 0.75f, true);

        @Override
        public synchronized void onGet(K key) {
            if (accessOrder.containsKey(key))
                accessOrder.get(key);
        }

        @Override
        public synchronized void onPut(K key) {
            accessOrder.put(key, Boolean.TRUE);
        }

        @Override
        public synchronized void onRemove(K key) {
            accessOrder.remove(key);
        }

        @Override
        public synchronized K evict() {
            if (accessOrder.isEmpty())
                return null;
            return accessOrder.entrySet().iterator().next().getKey();
        }
    }

    static class LFUCachePolicy<K> implements EvictionPolicy<K> {
        private final Map<K, Integer> freq = new HashMap<>();
        private final Map<Integer, LinkedHashSet<K>> buckets = new HashMap<>();
        private int minFreq = -1;

        private void addToBucket(int f, K key) {
            buckets.computeIfAbsent(f, k -> new LinkedHashSet<>()).add(key);
        }

        private void removeFromBucket(int f, K key) {
            LinkedHashSet<K> s = buckets.get(f);
            if (s != null) {
                s.remove(key);
                if (s.isEmpty())
                    buckets.remove(f);
            }
        }

        @Override
        public synchronized void onGet(K key) {
            Integer f = freq.get(key);
            if (f == null)
                return;
            removeFromBucket(f, key);
            int nf = f + 1;
            freq.put(key, nf);
            addToBucket(nf, key);
            if (!buckets.containsKey(minFreq))
                minFreq = buckets.keySet().stream().min(Integer::compareTo).orElse(-1);
        }

        @Override
        public synchronized void onPut(K key) {
            if (freq.containsKey(key)) {
                onGet(key);
                return;
            }
            freq.put(key, 1);
            addToBucket(1, key);
            minFreq = 1;
        }

        @Override
        public synchronized void onRemove(K key) {
            Integer f = freq.remove(key);
            if (f != null) {
                removeFromBucket(f, key);
                if (!buckets.containsKey(minFreq))
                    minFreq = buckets.keySet().stream().min(Integer::compareTo).orElse(-1);
            }
        }

        @Override
        public synchronized K evict() {
            if (minFreq == -1 || !buckets.containsKey(minFreq)) {
                if (buckets.isEmpty())
                    return null;
                minFreq = buckets.keySet().stream().min(Integer::compareTo).orElse(-1);
                if (minFreq == -1)
                    return null;
            }
            LinkedHashSet<K> set = buckets.get(minFreq);
            if (set == null || set.isEmpty())
                return null;
            return set.iterator().next();
        }
    }

    static class ThreadSafeCache<K, V> {
        private final int capacity;
        private final Map<K, V> store = new HashMap<>();
        private final EvictionPolicy<K> policy;
        private final ReentrantLock lock = new ReentrantLock();
        private long hits = 0, misses = 0;

        public ThreadSafeCache(int capacity, EvictionPolicy<K> policy) {
            if (capacity <= 0)
                throw new IllegalArgumentException("capacity > 0 required");
            this.capacity = capacity;
            this.policy = policy;
        }

        public V get(K key) {
            lock.lock();
            try {
                if (store.containsKey(key)) {
                    hits++;
                    policy.onGet(key);
                    return store.get(key);
                } else {
                    misses++;
                    return null;
                }
            } finally {
                lock.unlock();
            }
        }

        public void put(K key, V value) {
            lock.lock();
            try {
                if (store.containsKey(key)) {
                    store.put(key, value);
                    policy.onPut(key);
                    return;
                }
                if (store.size() >= capacity) {
                    K evictKey = policy.evict();
                    if (evictKey == null)
                        evictKey = store.keySet().iterator().next();
                    store.remove(evictKey);
                    policy.onRemove(evictKey);
                }
                store.put(key, value);
                policy.onPut(key);
            } finally {
                lock.unlock();
            }
        }

        public void remove(K key) {
            lock.lock();
            try {
                store.remove(key);
                policy.onRemove(key);
            } finally {
                lock.unlock();
            }
        }

        public CacheStats stats() {
            lock.lock();
            try {
                long total = hits + misses;
                double hr = (total == 0) ? 0.0 : (double) hits / total;
                return new CacheStats(capacity, store.size(), hits, misses, hr);
            } finally {
                lock.unlock();
            }
        }
    }

    static class CacheStats {
        public final int capacity, size;
        public final long hits, misses;
        public final double hitRate;

        public CacheStats(int capacity, int size, long hits, long misses, double hr) {
            this.capacity = capacity;
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hr;
        }

        public String toString() {
            return String.format("capacity=%d size=%d hits=%d misses=%d hitRate=%.4f", capacity, size, hits, misses,
                    hitRate);
        }
    }
}
