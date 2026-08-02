package com.scheduler.service;

import com.scheduler.dto.TaskResponse;
import com.scheduler.dto.WorkflowRequest;
import com.scheduler.dto.WorkflowResponse;
import com.scheduler.model.*;
import com.scheduler.repository.TaskDependencyRepository;
import com.scheduler.repository.TaskRepository;
import com.scheduler.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository workflowRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final KafkaProducerService kafkaProducerService;

    private static final java.util.regex.Pattern NAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]+$");

    public WorkflowService(WorkflowRepository workflowRepository,
                           TaskRepository taskRepository,
                           TaskDependencyRepository taskDependencyRepository,
                           KafkaProducerService kafkaProducerService) {
        this.workflowRepository = workflowRepository;
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    public WorkflowResponse createWorkflow(WorkflowRequest request) {

        if(request.getName() != null && !NAME_PATTERN.matcher(request.getName()).matches()){
            throw new IllegalArgumentException("Workflow name can only contain letters, digits, and underscores");
        }
        // Create workflow entity
        Workflow workflow = new Workflow();
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setStatus(WorkflowStatus.PENDING);
        workflow.setTotalTasks(request.getTasks().size());
        workflow = workflowRepository.save(workflow);

        // Map tempId -> real task ID
        Map<String, String> tempToRealId = new LinkedHashMap<>();
        List<Task> createdTasks = new ArrayList<>();

        // Topological sort: create tasks in dependency order
        List<WorkflowRequest.WorkflowTaskNode> sorted = topologicalSort(request.getTasks());

        for (WorkflowRequest.WorkflowTaskNode node : sorted) {
            Task task = new Task();
            task.setName(node.getName());
            task.setType(TaskType.valueOf(node.getType()));
            task.setPriority(node.getPriority() != null ? TaskPriority.valueOf(node.getPriority()) : TaskPriority.MEDIUM);
            task.setPayload(node.getPayload() != null ? node.getPayload() : "{}");
            task.setMaxRetries(node.getMaxRetries() != null ? node.getMaxRetries() : 3);
            task.setStatus(TaskStatus.PENDING);
            task.setWorkflowId(workflow.getId());
            task = taskRepository.save(task);

            tempToRealId.put(node.getTempId(), task.getId());
            createdTasks.add(task);

            // Save dependencies using mapped real IDs
            if (node.getDependsOn() != null) {
                for (String depTempId : node.getDependsOn()) {
                    String realDepId = tempToRealId.get(depTempId);
                    if (realDepId != null) {
                        taskDependencyRepository.save(new TaskDependency(task.getId(), realDepId));
                    }
                }
            }
        }

        // Submit root tasks (no dependencies) to Kafka
        for (WorkflowRequest.WorkflowTaskNode node : sorted) {
            if (node.getDependsOn() == null || node.getDependsOn().isEmpty()) {
                String realId = tempToRealId.get(node.getTempId());
                taskRepository.findById(realId).ifPresent(kafkaProducerService::submitTask);
            }
        }

        workflow.setStatus(WorkflowStatus.RUNNING);
        workflowRepository.save(workflow);

        log.info("[Workflow] Created '{}' with {} tasks (id={})",
                workflow.getName(), createdTasks.size(), workflow.getId());

        List<TaskResponse> taskResponses = createdTasks.stream()
                .map(TaskResponse::from)
                .collect(Collectors.toList());

        return WorkflowResponse.from(workflow, taskResponses);
    }

    public void updateWorkflowStatus(String workflowId) {
        if (workflowId == null) return;

        workflowRepository.findById(workflowId).ifPresent(workflow -> {
            List<Task> tasks = taskRepository.findByWorkflowId(workflowId);

            int completed = 0;
            int failed = 0;
            for (Task t : tasks) {
                if (t.getStatus() == TaskStatus.COMPLETED) completed++;
                else if (t.getStatus() == TaskStatus.FAILED) failed++;
            }

            workflow.setCompletedTasks(completed);
            workflow.setFailedTasks(failed);

            if (failed > 0) {
                workflow.setStatus(WorkflowStatus.FAILED);
                workflow.setCompletedAt(LocalDateTime.now());
                log.info("[Workflow] '{}' FAILED ({} completed, {} failed out of {})",
                        workflow.getName(), completed, failed, workflow.getTotalTasks());
            } else if (completed == workflow.getTotalTasks()) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
                workflow.setCompletedAt(LocalDateTime.now());
                log.info("[Workflow] '{}' COMPLETED all {} tasks",
                        workflow.getName(), workflow.getTotalTasks());
            } else {
                workflow.setStatus(WorkflowStatus.RUNNING);
            }

            workflowRepository.save(workflow);
        });
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> getAllWorkflows() {
        return workflowRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(WorkflowResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowResponse> getWorkflow(String id) {
        return workflowRepository.findById(id).map(workflow -> {
            List<TaskResponse> tasks = taskRepository.findByWorkflowId(id).stream()
                    .map(TaskResponse::from)
                    .collect(Collectors.toList());

            // Populate dependsOn / dependents for DAG visualization
            for (TaskResponse t : tasks) {
                List<String> deps = taskDependencyRepository.findByTaskId(t.getId()).stream()
                        .map(TaskDependency::getDependsOnTaskId)
                        .collect(Collectors.toList());
                List<String> depts = taskDependencyRepository.findByDependsOnTaskId(t.getId()).stream()
                        .map(TaskDependency::getTaskId)
                        .collect(Collectors.toList());
                t.setDependsOn(deps);
                t.setDependents(depts);
            }

            return WorkflowResponse.from(workflow, tasks);
        });
    }

    private List<WorkflowRequest.WorkflowTaskNode> topologicalSort(List<WorkflowRequest.WorkflowTaskNode> nodes) {
        Map<String, WorkflowRequest.WorkflowTaskNode> nodeMap = new LinkedHashMap<>();
        Map<String, Set<String>> adjList = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (WorkflowRequest.WorkflowTaskNode node : nodes) {
            nodeMap.put(node.getTempId(), node);
            adjList.put(node.getTempId(), new LinkedHashSet<>());
            inDegree.put(node.getTempId(), 0);
        }

        for (WorkflowRequest.WorkflowTaskNode node : nodes) {
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    if (adjList.containsKey(dep)) {
                        adjList.get(dep).add(node.getTempId());
                        inDegree.merge(node.getTempId(), 1, Integer::sum);
                    }
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<WorkflowRequest.WorkflowTaskNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(nodeMap.get(current));
            for (String neighbor : adjList.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) queue.add(neighbor);
            }
        }

        // If not all nodes sorted, there's a cycle - return original order as fallback
        if (sorted.size() < nodes.size()) {
            return nodes;
        }
        return sorted;
    }
}
