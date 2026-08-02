package com.scheduler.dto;

import java.util.Map;

// defining the hash ring details here

public class HashRingInfo {

    private int totalNodes;
    private int workerCount;
    private int virtualNodesPerWorker;
    private Map<String, Integer> distribution;

    public HashRingInfo() {}

    // constructor
    public HashRingInfo(int totalNodes, int workerCount, int virtualNodesPerWorker,
                        Map<String, Integer> distribution) {
        this.totalNodes = totalNodes;
        this.workerCount = workerCount;
        this.virtualNodesPerWorker = virtualNodesPerWorker;
        this.distribution = distribution;
    }

    // Getters and Setters

    public int getTotalNodes() { return totalNodes; }
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }

    public int getVirtualNodesPerWorker() { return virtualNodesPerWorker; }
    public void setVirtualNodesPerWorker(int virtualNodesPerWorker) { this.virtualNodesPerWorker = virtualNodesPerWorker; }

    public Map<String, Integer> getDistribution() { return distribution; }
    public void setDistribution(Map<String, Integer> distribution) { this.distribution = distribution; }
}
