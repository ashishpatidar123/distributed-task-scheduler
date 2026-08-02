package com.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent Hash Ring with virtual nodes for even distribution.
 *
 * Algorithm:
 * - Each physical worker gets N virtual nodes spread across a 32-bit hash ring
 * - Tasks are hashed to a position on the ring; the first node clockwise is selected
 * - Adding/removing a worker only redistributes ~1/N of the keys (minimal disruption)
 *
 * Uses MD5 for hash distribution (not cryptographic - just uniform spread).
 */
@Component
public class ConsistentHashRing {

    private static final Logger log = LoggerFactory.getLogger(ConsistentHashRing.class);

    private final NavigableMap<Long, String> ring = new ConcurrentSkipListMap<>();
    private final Map<String, Integer> workerVnodeCount = new LinkedHashMap<>();

    @Value("${scheduler.hash-ring.virtual-nodes:150}")
    private int virtualNodes;

    
    //  Add a worker to the hash ring with virtual nodes.
    
    public synchronized void addWorker(String workerId) {
        if (workerVnodeCount.containsKey(workerId)) return;

        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(workerId + "-vnode-" + i);
            ring.put(hash, workerId);
        }
        workerVnodeCount.put(workerId, virtualNodes);
        log.info("[HashRing] Added worker {} with {} virtual nodes. Ring size: {}",
                workerId, virtualNodes, ring.size());
    }

    
    //   Remove a worker and all its virtual nodes from the ring.
    
    public synchronized void removeWorker(String workerId) {
        if (!workerVnodeCount.containsKey(workerId)) return;

        ring.entrySet().removeIf(e -> e.getValue().equals(workerId));
        workerVnodeCount.remove(workerId);
        log.info("[HashRing] Removed worker {}. Ring size: {}", workerId, ring.size());
    }

    
    //   Find the worker responsible for the given key.
    //   Walks clockwise from the key's hash position to find the first node.
     
    //   @param key task routing key (e.g., "EMAIL:task-abc123")
    //   @return workerId, or null if ring is empty
    
    public String getWorker(String key) {
        if (ring.isEmpty()) return null;

        long hash = hash(key);

        // Find the first entry with hash >= key hash (clockwise)
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);

        // If no entry found, wrap around to the first entry in the ring
        if (entry == null) {
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    
    //  Get distribution statistics: how many virtual nodes each worker owns.
    
    public Map<String, Integer> getDistribution() {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (String workerId : workerVnodeCount.keySet()) {
            dist.put(workerId, 0);
        }
        for (String workerId : ring.values()) {
            dist.merge(workerId, 1, Integer::sum);
        }
        return dist;
    }

    
    //  Get all node positions on the ring (for visualization).
    //  Returns a map of hash position -> workerId (only physical node positions for clarity).
    
    public Map<Long, String> getRingPositions() {
        return Collections.unmodifiableMap(new TreeMap<>(ring));
    }

    public int getNodeCount() { return ring.size(); }
    public int getWorkerCount() { return workerVnodeCount.size(); }
    public int getVirtualNodeCount() { return virtualNodes; }

    
    //  Hash function using MD5 for uniform distribution across the ring.
    //  Takes first 4 bytes of MD5 digest as a long.
    
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Use first 4 bytes to create a positive long
            return ((long)(digest[0] & 0xFF) << 24)
                 | ((long)(digest[1] & 0xFF) << 16)
                 | ((long)(digest[2] & 0xFF) << 8)
                 | ((long)(digest[3] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return key.hashCode() & 0xFFFFFFFFL;
        }
    }
}
