package com.scheduler.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.*;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scheduler.dto.DagValidationResult;
import com.scheduler.dto.HashRingInfo;
import com.scheduler.dto.SchedulerStats;
import com.scheduler.dto.TaskRequest;
import com.scheduler.dto.TaskResponse;
import com.scheduler.dto.WorkflowRequest;
import com.scheduler.dto.WorkflowResponse;
import com.scheduler.model.DlqEntry;
import com.scheduler.model.Task;
import com.scheduler.model.TaskAttempt;
import com.scheduler.model.TaskStatus;
import com.scheduler.model.WorkerInfo;
import com.scheduler.repository.*;
import com.scheduler.service.ConsistentHashRing;
import com.scheduler.service.DagService;
import com.scheduler.service.KafkaProducerService;
import com.scheduler.service.RateLimiterService;
import com.scheduler.service.SchedulerService;
import com.scheduler.service.SimulationConfigService;
import com.scheduler.service.TaskService;
import com.scheduler.service.WorkflowService;

import jakarta.servlet.http.HttpServletResponse;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final SchedulerService schedulerService;
    private final KafkaProducerService kafkaProducerService;
    private final TaskAttemptRepository taskAttemptRepository;
    private final DagService dagService;
    private final WorkflowService workflowService;
    private final SimulationConfigService simulationConfigService;
    private final DlqRepository dlqRepository;
    private final RateLimiterService rateLimiterService;
    

    public TaskController(TaskService taskService, SchedulerService schedulerService,
                          KafkaProducerService kafkaProducerService,
                        TaskAttemptRepository taskAttemptRepository, DagService dagService,
                        SimulationConfigService simulationConfigService,
                        WorkflowService workflowService, DlqRepository dlqRepository,
                            RateLimiterService rateLimiterService){
        this.taskService = taskService;
        this.schedulerService = schedulerService;
        this.kafkaProducerService = kafkaProducerService;
        this.taskAttemptRepository = taskAttemptRepository;
        this.dagService = dagService;
        this.simulationConfigService = simulationConfigService;
        this.workflowService = workflowService;
        this.dlqRepository = dlqRepository;
        this.rateLimiterService = rateLimiterService;
        

    }

    // the main tasks api, to submit tasks

    @PostMapping("/tasks")
    public ResponseEntity<?> submitTask(@RequestBody TaskRequest request) {

        // first checking the queue, if it;s at it's max capacity then don't allow the task to get taskSubmissionListenerFactory
        //  show service unavailbility status
        if(schedulerService.isQueueFull()){
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Task queue is full",
                                "message", "The task queue has reached its maximum capacity. Please try again later."));
        }

        // if there are too many requests for task submission for a given type then
        // then show http message of too many requests 
        if(!rateLimiterService.tryAcquire(request.getType())){
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Rate limit exceeded for "+ request.getType(),
                                "message", "Too many requests. Please wait and try again"));
        }
        Task task = taskService.createTask(request);
        kafkaProducerService.submitTask(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(task));
    }

    
    // fetch all tasks from the db with pagination
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getAllTasks(
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "10") int size,
                @RequestParam(required = false) String status,
                @RequestParam(required = false) String type,
                @RequestParam(required = false) String priority,
                @RequestParam(required = false) String name,
                @RequestParam(required = false) String id,
                @RequestParam(required = false) String worker,
                @RequestParam(defaultValue = "createdAt") String sortBy,
                @RequestParam(defaultValue = "desc") String sortDir){

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Specification<Task> spec = Specification.where(TaskSpecification.hasStatus(status))
                .and(TaskSpecification.hasType(type))
                .and(TaskSpecification.hasPriority(priority))
                .and(TaskSpecification.nameLike(name))
                .and(TaskSpecification.idLike(id))
                .and(TaskSpecification.workerLike(worker));

        Page<Task> result = taskService.getFilteredTasks(spec, pageable);

        List<TaskResponse> taskResponses = result.getContent().stream()
                .map(TaskResponse::from)
                .collect(Collectors.toList());

        Map<String, Object> response = Map.of(

                "content", taskResponses,
                "currentPage", result.getNumber(),
                "totalItems", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "pageSize", result.getSize()
        );

        return ResponseEntity.ok(response);
    }
    
        
    
    // tasks with given id
    @GetMapping("/tasks/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable String id) {
        return taskService.getTask(id)
                .map(task -> ResponseEntity.ok(TaskResponse.from(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    // delete a tasks with given id
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<?> cancelTask(@PathVariable String id) {
        try {
            Task task = taskService.cancelTask(id);
            return ResponseEntity.ok(TaskResponse.from(task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
        }
    }

    // get all the stats
    @GetMapping("/stats")
    public ResponseEntity<SchedulerStats> getStats() {
        return ResponseEntity.ok(schedulerService.getStats());
    }

    // get the list of workers
    @GetMapping("/workers")
    public ResponseEntity<Collection<WorkerInfo>> getWorkers() {
        return ResponseEntity.ok(schedulerService.getWorkers());
    }

    // post a retry request for a failed task
    @PostMapping("/tasks/{id}/retry")
    public ResponseEntity<?> retryTask(@PathVariable String id) {
        // if the task is neither failed nor cancelled, then don't allow retry
        return taskService.getTask(id)
                .map(task -> {
                    if (task.getStatus() != TaskStatus.FAILED &&
                        task.getStatus() != TaskStatus.CANCELLED) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Cannot retry task in status: " + task.getStatus()));
                    }
                    
                    // mark pending
                    task.setStatus(TaskStatus.PENDING);
                    task.setErrorMessage(null);
                    task.setResult(null);
                    task.setStartedAt(null);
                    task.setCompletedAt(null);
                    task.setExecutionTimeMs(0);
                    task.setAssignedWorker(null);

                    taskService.updateTask(task);
                    kafkaProducerService.submitTask(task);
                    return ResponseEntity.ok().body((Object) TaskResponse.from(task));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // get attempts of a task
    @GetMapping("/tasks/{id}/attempts")
    public ResponseEntity<List<TaskAttempt>> getTaskAttempts(@PathVariable String id) {
        return taskService.getTask(id)
                .map(task -> ResponseEntity.ok(taskAttemptRepository.findByTaskIdOrderByAttemptNumberAsc(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    // export the tasks list as a csv file
    
    @GetMapping("/tasks/export")
    public void exportTasksCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=tasks.csv");

        List<Task> tasks = taskService.getAllTasks();
        PrintWriter writer = response.getWriter();
        writer.println("ID,Name,Type,Priority,Status,Worker,ExecutionTimeMs,RetryCount,MaxRetries,CreatedAt,StartedAt,CompletedAt,Error,Result");

        for (Task t : tasks) {
            writer.printf("%s,%s,%s,%s,%s,%s,%d,%d,%d,%s,%s,%s,\"%s\",\"%s\"\n",
                t.getId(),
                csvEscape(t.getName()),
                t.getType(),
                t.getPriority(),
                t.getStatus(),
                t.getAssignedWorker() != null ? t.getAssignedWorker() : "",
                t.getExecutionTimeMs(),
                t.getRetryCount(),
                t.getMaxRetries(),
                t.getCreatedAt() != null ? t.getCreatedAt() : "",
                t.getStartedAt() != null ? t.getStartedAt() : "",
                t.getCompletedAt() != null ? t.getCompletedAt() : "",
                t.getErrorMessage() != null ? t.getErrorMessage().replace("\"", "\"\"") : "",
                t.getResult() != null ? t.getResult().replace("\"", "\"\"") : ""
            );
        }
        writer.flush();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        SchedulerStats stats = schedulerService.getStats();
        Map<String, Object> health = Map.of(
            "status", "UP",
            "workers", stats.getActiveWorkers() + stats.getIdleWorkers(),
            "queueSize", stats.getQueueSize(),
            "totalTasks", stats.getTotalTasks()
        );
        return ResponseEntity.ok(health);
    }

    @GetMapping("/dag/validate")
    public ResponseEntity<DagValidationResult> validateDag() {
        return ResponseEntity.ok(dagService.validate());
    }
    
    @GetMapping("/tasks/{id}/dependencies")
    public ResponseEntity<List<String>> getTaskDependencies(@PathVariable String id) {
        return taskService.getTask(id)
                .map(task -> ResponseEntity.ok(dagService.getDependencies(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{id}/dependents")
    public ResponseEntity<List<String>> getTaskDependents(@PathVariable String id) {
        return taskService.getTask(id)
                .map(task -> ResponseEntity.ok(dagService.getDependents(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/hashring")
    public ResponseEntity<HashRingInfo> getHashRing(){
        ConsistentHashRing ring = schedulerService.getHashRing();
        HashRingInfo info = new HashRingInfo(
            ring.getNodeCount(),
            ring.getWorkerCount(),
            ring.getVirtualNodeCount(),
            ring.getDistribution()
        );
        return ResponseEntity.ok(info);
    }

    

    // ==================== Workflows ====================

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowResponse> createWorkflow(@RequestBody WorkflowRequest request) {
        WorkflowResponse response = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workflows")
    public ResponseEntity<List<WorkflowResponse>> getAllWorkflows() {
        return ResponseEntity.ok(workflowService.getAllWorkflows());
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable String id) {
        return workflowService.getWorkflow(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Simulation Config ====================

    @GetMapping("/config/failure-rates")
    public ResponseEntity<Map<String, Integer>> getFailureRates() {
        return ResponseEntity.ok(simulationConfigService.getAllFailureRates());
    }

    @PutMapping("/config/failure-rates")
    public ResponseEntity<Map<String, Integer>> updateFailureRates(@RequestBody Map<String, Integer> rates) {
        simulationConfigService.updateAllFailureRates(rates);
        return ResponseEntity.ok(simulationConfigService.getAllFailureRates());
    }

    

    @GetMapping("/dlq")
    public  ResponseEntity<List<DlqEntry>> getDlqEntries() {
        return ResponseEntity.ok(dlqRepository.findAllByOrderByFailedAtDesc());
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<?> replayDlqEntry(@PathVariable String id){
        return dlqRepository.findById(id)
                .map(entry -> {
                    if(entry.isReplayed()){
                        return ResponseEntity.badRequest().body(Map.of("error","Already replayed"));
                    }

                    // create a fresh task from the dlq entry
                    TaskRequest request =  new TaskRequest();
                    request.setName(entry.getTaskName() + "_replay");
                    request.setType(entry.getTaskType());
                    request.setPayload(entry.getPayload());
                    request.setPriority(entry.getPriority());
                    request.setMaxRetries(entry.getMaxRetries());

                    Task newTask = taskService.createTask(request);
                    kafkaProducerService.submitTask(newTask);

                    // mark dlq entry as replayed
                    entry.setReplayed(true);
                    entry.setReplayedAt(java.time.LocalDateTime.now());
                    dlqRepository.save(entry);

                    return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(newTask));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    // ==================== Rate Limiter ====================

    @GetMapping("/rate-limits")
    public ResponseEntity<Map<String, Object>> getRateLimits() {
        return ResponseEntity.ok(rateLimiterService.getStatus());
    }

    @PutMapping("/rate-limits")
    public ResponseEntity<Map<String, Object>> updateRateLimits(@RequestBody Map<String, Object> config) {
        if (config.containsKey("enabled")) {
            rateLimiterService.setEnabled((Boolean) config.get("enabled"));
        }
        if (config.containsKey("globalCapacity") && config.containsKey("globalRefillPerSec")) {
            rateLimiterService.updateGlobal(
                ((Number) config.get("globalCapacity")).intValue(),
                ((Number) config.get("globalRefillPerSec")).doubleValue());
        }
        if (config.containsKey("perTypeCapacity") && config.containsKey("perTypeRefillPerSec")) {
            rateLimiterService.updatePerType(
                ((Number) config.get("perTypeCapacity")).intValue(),
                ((Number) config.get("perTypeRefillPerSec")).doubleValue());
        }
        return ResponseEntity.ok(rateLimiterService.getStatus());
    }
    private static final Pattern OUTPUT_PATH_PATTERN = Pattern.compile("(data[/\\\\](?:output)[/\\\\](?:[\\w._-]+[/\\\\])?[\\w._-]+)");

    @GetMapping("/tasks/{id}/output")
    public ResponseEntity<Resource> getTaskOutput(@PathVariable String id) {
        return taskService.getTask(id)
                .map(task -> {
                    String result = task.getResult();
                    if (result == null || result.isEmpty()) {
                        return ResponseEntity.notFound().<Resource>build();
                    }

                    // Extract file path from result string (e.g., "... -> data/output/users_sorted_123.csv")
                    Matcher matcher = OUTPUT_PATH_PATTERN.matcher(result);
                    if (!matcher.find()) {
                        return ResponseEntity.notFound().<Resource>build();
                    }

                    Path filePath = Paths.get(matcher.group(1)).normalize();
                    
                    if (!filePath.startsWith("data")) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Resource>build();
                    }
                    
                    if (!Files.exists(filePath)) {
                        return ResponseEntity.notFound().<Resource>build();
                    }

                    try {
                        Resource resource = new FileSystemResource(filePath.toFile());
                        String contentType = "text/csv";

                        return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(contentType))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "inline; filename=\"" + filePath.getFileName() + "\"")
                                .body(resource);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Resource>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

// ================== Data File Preview ==================

    // ================= Data File Preview =================
    @GetMapping("/data/files")
    public ResponseEntity<List<String>> listDataFiles() {
        try {
            Path dataDir = Paths.get("data");
            if (!Files.exists(dataDir)) return ResponseEntity.ok(List.of());
            List<String> csvFiles = Files.walk(dataDir, 1)
                    .filter(p -> p.toString().endsWith(".csv"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
            return ResponseEntity.ok(csvFiles);
        } catch (IOException e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/data/preview/{filename}")
    public ResponseEntity<?> previewDataFile(@PathVariable String filename) {
        if (!filename.endsWith(".csv") || filename.contains("..") || filename.contains("/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filename"));
        }
        Path filePath = Paths.get("data", filename);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        try {
            List<String> lines = Files.readAllLines(filePath);
            if (lines.isEmpty()) return ResponseEntity.ok(Map.of("headers", List.of(), "rows", List.of()));
            String[] headers = lines.get(0).split(",");
            List<String[]> rows = lines.stream().skip(1).limit(5)
                    .map(l -> l.split(",")).collect(Collectors.toList());
            return ResponseEntity.ok(Map.of(
                    "filename", filename,
                    "headers", List.of(headers),
                    "sampleRows", rows,
                    "totalRows", lines.size() - 1
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read file"));
        }
    }


    

}