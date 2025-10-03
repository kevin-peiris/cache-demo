import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * Concurrent LRU Cache Project
 *
 * Notes :
 * - An LRU cache is just a fast way to store recently used items.
 * - If it gets full, we throw away the "oldest" item (least recently used).
 * - Used a HashMap for fast key lookup and a doubly linked list to track order.
 *
 * Steps done:
 * 1. First made a simple LRU cache (no threads).
 * 2. Then added thread safety using locks so multiple threads can use it.
 * 3. Wrote tests at the bottom (main function) to check both versions.
 *
 * About performance:
 * - get() and put() are O(1) because HashMap + linked list
 * - concurrency: Used ReadWriteLock (many readers, one writer)
 * - probably not the fastest, but it’s simple and works
 */
public class MyLRUCache {

    // Node for linked list
    static class Node<K,V> {
        K key;
        V value;
        Node<K,V> prev, next;
        Node(K k, V v) { key = k; value = v; }
    }

    // ===== BASIC LRU CACHE =====
    static class LRUCache<K,V> {
        private final int capacity;
        protected final Map<K, Node<K,V>> map;
        private final Node<K,V> head, tail;

        public LRUCache(int cap) {
            this.capacity = cap;
            this.map = new HashMap<>();
            this.head = new Node<>(null,null);
            this.tail = new Node<>(null,null);
            head.next = tail; tail.prev = head;
        }

        public V get(K key) {
            Node<K,V> node = map.get(key);
            if (node == null) return null;
            moveToHead(node);
            return node.value;
        }

        public void put(K key, V value) {
            Node<K,V> node = map.get(key);
            if (node != null) {
                node.value = value;
                moveToHead(node);
            } else {
                Node<K,V> n = new Node<>(key, value);
                map.put(key, n);
                addToHead(n);
                if (map.size() > capacity) {
                    Node<K,V> old = removeTail();
                    map.remove(old.key);
                }
            }
        }

        private void addToHead(Node<K,V> node) {
            node.prev = head;
            node.next = head.next;
            head.next.prev = node;
            head.next = node;
        }

        private void moveToHead(Node<K,V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            addToHead(node);
        }

        private Node<K,V> removeTail() {
            Node<K,V> last = tail.prev;
            last.prev.next = tail;
            tail.prev = last.prev;
            return last;
        }
    }

    // ===== THREAD-SAFE LRU CACHE =====
    static class ConcurrentLRU<K,V> extends LRUCache<K,V> {
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        public ConcurrentLRU(int cap) {
            super(cap);
        }

        @Override
        public V get(K key) {
            lock.readLock().lock();
            try {
                return super.get(key);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void put(K key, V value) {
            lock.writeLock().lock();
            try {
                super.put(key, value);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    // ===== TESTING =====
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Basic LRU test:");
        LRUCache<Integer,String> basic = new LRUCache<>(2);
        basic.put(1,"one");
        basic.put(2,"two");
        System.out.println("Get(1): " + basic.get(1)); // expect "one"
        basic.put(3,"three"); // should evict 2
        System.out.println("Get(2): " + basic.get(2)); // null

        System.out.println("\nConcurrent LRU test with threads:");
        ConcurrentLRU<Integer,String> conc = new ConcurrentLRU<>(3);
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (int i=0; i<10; i++) {
            final int key = i;
            pool.submit(() -> {
                conc.put(key, "val"+key);
                String got = conc.get(key);
                if (got == null) {
                    System.out.println("Thread problem with key " + key);
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("Concurrent test done, final size = " + conc.map.size());

        // very basic "performance" style note
        long start = System.nanoTime();
        for (int i=0; i<100000; i++) {
            basic.put(i,"num"+i);
            basic.get(i);
        }
        long end = System.nanoTime();
        System.out.println("Basic LRU 100k ops took " + (end-start)/1e6 + " ms");
    }
}
