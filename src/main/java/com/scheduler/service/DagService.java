package com.scheduler.service;

import com.scheduler.dto.DagValidationResult;
import com.scheduler.model.TaskDependency;
import com.scheduler.model.TaskStatus;
import com.scheduler.repository.TaskDependencyRepository;
import com.scheduler.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DagService {

    private static final Logger log = LoggerFactory.getLogger(DagService.class);

    private final TaskDependencyRepository dependencyRepository;
    private final TaskRepository taskRepository;

    public DagService(TaskDependencyRepository dependencyRepository, TaskRepository taskRepository) {
        this.dependencyRepository = dependencyRepository;
        this.taskRepository = taskRepository;
    }

    // checking if all dependencies of a tasks are completed or not
    public boolean areDependenciesMet(String taskId) {

        List<TaskDependency> deps = dependencyRepository.findByTaskId(taskId);
        // if no dependencies then return true
        if (deps.isEmpty()) return true;

        for (TaskDependency dep : deps) {
            var optTask = taskRepository.findById(dep.getDependsOnTaskId());
            // if any dependency is not completed then return false
            if (optTask.isEmpty() || optTask.get().getStatus() != TaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    // find the tasks which depends on a given task
    public List<String> getDependents(String taskId) {
        return dependencyRepository.findByDependsOnTaskId(taskId).stream()
                .map(TaskDependency::getTaskId)
                .collect(Collectors.toList());
    }

    
    // Get tasks that the given task depends on (upstream dependencies).
    
    public List<String> getDependencies(String taskId) {
        return dependencyRepository.findByTaskId(taskId).stream()
                .map(TaskDependency::getDependsOnTaskId)
                .collect(Collectors.toList());
    }

    // using kahns algorithm for finding a cycle 
    public DagValidationResult validate() {
        List<TaskDependency> allDeps = dependencyRepository.findAll();

        if (allDeps.isEmpty()) {
            return DagValidationResult.valid(Collections.emptyList());
        }

        // Build adjacency list and in-degree map
        Set<String> allNodes = new HashSet<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (TaskDependency dep : allDeps) {
            allNodes.add(dep.getDependsOnTaskId());
            allNodes.add(dep.getTaskId());
            adjacency.computeIfAbsent(dep.getDependsOnTaskId(), k -> new ArrayList<>())
                    .add(dep.getTaskId());
            inDegree.merge(dep.getTaskId(), 1, Integer::sum);
            inDegree.putIfAbsent(dep.getDependsOnTaskId(), 0);
        }

        // Kahn's Algorithm: BFS-based topological sort
        Queue<String> queue = new LinkedList<>();
        for (String node : allNodes) {
            if (inDegree.getOrDefault(node, 0) == 0) {
                queue.add(node);
            }
        }

        List<String> topOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            topOrder.add(node);

            for (String neighbor : adjacency.getOrDefault(node, Collections.emptyList())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (topOrder.size() == allNodes.size()) {
            log.info("[DAG] Validation passed. Topological order: {}", topOrder);
            return DagValidationResult.valid(topOrder);
        } else {
            // Cycle detected - find the cycle using DFS
            List<String> cycle = findCycleDFS(allNodes, adjacency);
            log.warn("[DAG] Cycle detected: {}", cycle);
            return DagValidationResult.invalid(cycle);
        }
    }

    // usinf dfs for finding the cycle - the list of tasks which forms the cycle
    
    private List<String> findCycleDFS(Set<String> nodes, Map<String, List<String>> adjacency) {
        Map<String, Integer> color = new HashMap<>(); // 0=white, 1=gray, 2=black
        Map<String, String> parent = new HashMap<>();

        for (String node : nodes) color.put(node, 0);

        for (String node : nodes) {
            if (color.get(node) == 0) {
                List<String> cycle = dfs(node, adjacency, color, parent);
                if (cycle != null) return cycle;
            }
        }
        return Collections.singletonList("Unknown cycle");
    }

    private List<String> dfs(String node, Map<String, List<String>> adj,
                             Map<String, Integer> color, Map<String, String> parent) {
        color.put(node, 1); // Mark as GRAY (visiting)

        for (String neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            if (color.getOrDefault(neighbor, 0) == 1) {
                // Found a cycle - reconstruct it
                List<String> cycle = new ArrayList<>();
                cycle.add(neighbor);
                String current = node;
                while (!current.equals(neighbor)) {
                    cycle.add(current);
                    current = parent.getOrDefault(current, neighbor);
                }
                cycle.add(neighbor); // Complete the cycle
                Collections.reverse(cycle);
                return cycle;
            }
            if (color.getOrDefault(neighbor, 0) == 0) {
                parent.put(neighbor, node);
                List<String> cycle = dfs(neighbor, adj, color, parent);
                if (cycle != null) return cycle;
            }
        }

        color.put(node, 2); // Mark as BLACK (done)
        return null;
    }
}