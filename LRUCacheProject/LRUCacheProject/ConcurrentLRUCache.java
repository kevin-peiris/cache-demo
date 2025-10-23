package LRUCacheProject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ConcurrentLRUCache - Thread-safe LRU cache implementation
 * 
 * This cache uses ConcurrentHashMap for the main storage and a doubly-linked
 * list to track access order. I chose this design because:
 * - ConcurrentHashMap gives us good concurrent performance without locking everything
 * - The linked list lets us track what was used recently in O(1) time
 * - Only the list structure needs a lock, not the whole cache
 * 
 * @author Kevin Peiris
 * @param <K> key type
 * @param <V> value type
 */
public class ConcurrentLRUCache<K, V> {

    private final int capacity;
    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final Node<K, V> head;  // dummy node - most recent is after this
    private final Node<K, V> tail;  // dummy node - least recent is before this
    private final ReentrantLock listLock = new ReentrantLock();

    // Inner class for linked list nodes
    private static class Node<K, V> {
        final K key;
        volatile V value;  // volatile so threads see updates
        Node<K, V> prev, next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Creates a new LRU cache with specified capacity.
     * @param capacity maximum number of entries (must be at least 1)
     */
    public ConcurrentLRUCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        // Initialize map with a reasonable size to avoid resizing
        this.map = new ConcurrentHashMap<>(Math.max(16, capacity * 2));
        
        // Set up dummy head and tail for the linked list
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    /**
     * Retrieves value for given key. Returns null if not found.
     * Accessing a key marks it as recently used.
     */
    public V get(K key) {
        if (key == null) {
            return null;
        }
        
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }

        // Mark this entry as recently used by moving to head
        moveToHead(node);
        return node.value;
    }

    /**
     * Adds or updates an entry in the cache.
     * If cache is full, evicts the least recently used entry.
     */
    public void put(K key, V value) {
    if (key == null) throw new NullPointerException("Key cannot be null");
    if (value == null) throw new NullPointerException("Value cannot be null");

    listLock.lock();
    try {
        Node<K, V> node = map.get(key);
        if (node != null) {
            // Update value and move to head
            node.value = value;
            removeNode(node);
            addToHead(node);
            return;
        }

        // Insert new node
        Node<K, V> newNode = new Node<>(key, value);
        map.put(key, newNode);
        addToHead(newNode);

        // Evict LRU until we are within capacity
        while (map.size() > capacity) {
            Node<K, V> lru = removeTail();
            if (lru != null && lru.key != null) {
                map.remove(lru.key);
            }
        }
    } finally {
        listLock.unlock();
    }
}


    /**
     * Returns current number of entries in cache.
     */
    public int size() {
        return map.size();
    }

    /**
     * Removes all entries from the cache.
     */
    public void clear() {
        listLock.lock();
        try {
            head.next = tail;
            tail.prev = head;
            map.clear();
        } finally {
            listLock.unlock();
        }
    }

    /**
     * Checks if cache contains the given key.
     */
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    // --- Private helper methods ---

    private void moveToHead(Node<K, V> node) {
        // Quick optimization: if already at head, skip locking
        if (node.prev == head) {
            return;
        }

        listLock.lock();
        try {
            removeNode(node);
            addToHead(node);
        } finally {
            listLock.unlock();
        }
    }

    private void addToHead(Node<K, V> node) {
        Node<K, V> first = head.next;
        node.prev = head;
        node.next = first;
        head.next = node;
        first.prev = node;
    }

    private void removeNode(Node<K, V> node) {
        Node<K, V> p = node.prev;
        Node<K, V> n = node.next;
        if (p != null && n != null) {
            p.next = n;
            n.prev = p;
            node.prev = null;
            node.next = null;
        }
    }

    private Node<K, V> removeTail() {
        Node<K, V> last = tail.prev;
        if (last == head) {
            return null;  // empty
        }
        removeNode(last);
        return last;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ConcurrentLRUCache{size=").append(size());
        sb.append(", capacity=").append(capacity).append("}");
        return sb.toString();
    }
}