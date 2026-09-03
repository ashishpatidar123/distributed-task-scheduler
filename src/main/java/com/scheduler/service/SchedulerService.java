package com.scheduler.service;

import com.scheduler.dto.*;
import com.scheduler.handler.TaskHandler;
import com.scheduler.model.*;
import com.scheduler.repository.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class SchedulerService {

    

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final TaskRepository taskRepository;
    private final TaskAttemptRepository taskAttemptRepository;
    private final RetryService retryService;
    private final NotificationService notificationService;
    private final KafkaProducerService kafkaProducerService;
    private final Map<TaskType, TaskHandler> handlers;
    private final ConsistentHashRing hashRing;
    
    private final DagService dagService;
    private  WorkflowService workflowService;
    private final DlqRepository dlqRepository;
    private final RateLimiterService rateLimiterService;

    private final PriorityBlockingQueue<TaskQueueItem> taskQueue;
    private final ConcurrentHashMap<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    
    private final Set<String> enqueuedTaskIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Long> completionTimestamps = new ConcurrentLinkedQueue<> ();
    private ExecutorService workerPool;
    private volatile boolean running = true;
    
    private final java.util.concurrent.atomic.AtomicLong recoveredTaskCount = new java.util.concurrent.atomic.AtomicLong(0);

    private final MeterRegistry meterRegistry;
    private final Counter taskSubmmitedCounter;
    private final Counter taskCompletedCounter;
    private final Counter taskFailedCounter;
    private final Timer taskExecutionTimer;

    @Value("${scheduler.worker-count:4}")
    private int workerCount;

    @Value("${scheduler.heartbeat-timeout-ms:15000}")
    private long heartbeatTimeoutMs;

    @Value("${scheduler.queue-capacity:100}")
    private int maxQueueCapacity;

    @Value("${scheduler.starvation-boost-interval-ms:10000}")
    private long starvationBoostIntervalMs;

    public void setWorkflowService(WorkflowService workflowService){
        this.workflowService = workflowService;
    }

    public SchedulerService(TaskRepository taskRepository,
                            TaskAttemptRepository taskAttemptRepository,
                            RetryService retryService,
                            NotificationService notificationService,
                            KafkaProducerService kafkaProducerService,
                            List<TaskHandler> handlerList,
                            @Value("${scheduler.queue-capacity:100}") int queueCapacity, DagService dagService,
                        ConsistentHashRing hashRing,
                        DlqRepository dlqRepository, RateLimiterService rateLimiterService, MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.taskAttemptRepository = taskAttemptRepository;
        this.retryService = retryService;
        this.notificationService = notificationService;
        this.kafkaProducerService = kafkaProducerService;
        this.taskQueue = new PriorityBlockingQueue<>(queueCapacity);
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(TaskHandler::getType, h -> h));
        this.dagService = dagService;
        this.hashRing = hashRing;
        
        this.rateLimiterService = rateLimiterService;
        this.dlqRepository = dlqRepository;
        this.meterRegistry = meterRegistry;
        this.taskCompletedCounter = Counter.builder("scheduler.tasks.submitter").register(meterRegistry);
        this.taskSubmmitedCounter = Counter.builder("scheduler.tasks.completed").register(meterRegistry);
        this.taskFailedCounter = Counter.builder("scheduler.tasks.failed").register(meterRegistry);
        this.taskExecutionTimer = Timer.builder("scheduler.tasks.execution.time").register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        log.info("Starting Distributed Task Scheduler with {} workers", workerCount);
        workerPool = Executors.newCachedThreadPool();

        for (int i = 1; i <= workerCount; i++) {
            String id = "worker-" + i;
            String name = "Worker-" + i;
            WorkerInfo worker = new WorkerInfo(id, name);
            workers.put(id, worker);
            hashRing.addWorker(id);
            workerPool.submit(() -> workerLoop(worker));
            log.info("Started {}", name);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Graceful shutdown initiated - stopping new task processing and waiting for running tasks to complete");
        running = false;

        int drained = 0;
        TaskQueueItem item;
        while ((item = taskQueue.poll()) != null) {
            taskRepository.findById(item.getTaskId()).ifPresent(task -> {
                if(task.getStatus() == TaskStatus.QUEUED){
                    task.setStatus(TaskStatus.PENDING);
                    task.setAssignedWorker(null);
                    taskRepository.save(task);
                    log.debug("Drained task {} from queue and marked as PENDING", task.getId());
                }
                
            });
            enqueuedTaskIds.remove(item.getTaskId());
            drained++;
        }

        log.info("Drained {} tasks from the queue and marked them as PENDING", drained);

        workerPool.shutdown();
        try{
            log.info("Waiting up to 30 seconds for running tasks to complete...");
            if(!workerPool.awaitTermination(30, TimeUnit.SECONDS)){
                log.warn("Timeout reached - forcing shutdown of running tasks");
                workerPool.shutdownNow();
                workerPool.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
        catch(InterruptedException e){
            log.error("Shutdown interrupted: {}", e.getMessage());
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Scheduler stopped gracefully");
    }

    // ==================== PRODUCER: Enqueue Tasks ====================

    public void enqueue(Task task) {
        if (enqueuedTaskIds.contains(task.getId())) {
            return; // Already in queue
        }

        // don't enqueue tasks which are already picked by worker
        TaskStatus currentStatus = task.getStatus();
        if(currentStatus == TaskStatus.RUNNING || currentStatus == TaskStatus.QUEUED || currentStatus == TaskStatus.COMPLETED){
            log.debug("Task {} already in state {}, skipping enqueue", task.getId(), currentStatus);
            return;
        }

        task.setStatus(TaskStatus.QUEUED);
        taskRepository.save(task);
        enqueuedTaskIds.add(task.getId());
        taskQueue.offer(new TaskQueueItem(task.getId(), task.getPriority(), task.getCreatedAt()));
        taskSubmmitedCounter.increment();
        log.info("Task {} enqueued [priority={}], effectiveBoostInterval={}ms - queue size: {}",
                task.getId(), task.getPriority(), starvationBoostIntervalMs, taskQueue.size());
        kafkaProducerService.publishEvent(
                TaskEvent.of(task.getId(), task.getName(), TaskStatus.QUEUED, null));
    }

    // ==================== CONSUMER: Worker Loop ====================

    private void workerLoop(WorkerInfo worker) {
        log.info("{} started and waiting for tasks", worker.getName());

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Poll with timeout so we can check the running flag
                TaskQueueItem item = taskQueue.poll(1, TimeUnit.SECONDS);

                if (item != null) {
                    executeTask(worker, item.getTaskId());
                }

                // Heartbeat update
                worker.updateHeartbeat();
                worker.setStatus(WorkerStatus.IDLE);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("{} encountered error: {}", worker.getName(), e.getMessage());
                worker.setStatus(WorkerStatus.IDLE);
            }
        }

        worker.setStatus(WorkerStatus.OFFLINE);
        log.info("{} stopped", worker.getName());
    }

    private void executeTask(WorkerInfo worker, String taskId) {

        MDC.put("taskId", taskId);
        log.info("{} picked up task {}", worker.getName(), taskId);
        MDC.put("workerName", worker.getName());
        
        try{
            Optional<Task> optTask = taskRepository.findById(taskId);
            if (optTask.isEmpty()) {
                log.warn("Task {} not found in DB, skipping", taskId);
                return;
            }

            Task task = optTask.get();

            // Skip if cancelled
            if (task.getStatus() == TaskStatus.CANCELLED) {
                log.info("Task {} was cancelled, skipping", taskId);
                enqueuedTaskIds.remove(taskId);
                return;
            }

            TaskStatus currentStatus = task.getStatus();
            if(currentStatus == TaskStatus.RUNNING || currentStatus == TaskStatus.COMPLETED){
                log.debug("Task {} already in state {}, skipping duplicate execution", task.getId(), currentStatus);
                enqueuedTaskIds.remove(taskId);
                return;
            }

            // Mark as RUNNING
            worker.setStatus(WorkerStatus.BUSY);
            worker.setCurrentTaskId(taskId);
            task.setStatus(TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task.setAssignedWorker(worker.getName());
            taskRepository.save(task);
            kafkaProducerService.publishEvent(
                    TaskEvent.of(task.getId(), task.getName(), TaskStatus.RUNNING, worker.getName()));

            log.info("{} executing task {} [{}] - '{}'",
                    worker.getName(), task.getId(), task.getType(), task.getName());

            String hashKey = task.getType() + ":" + task.getId();
            String assignedByRing = hashRing.getWorker(hashKey);
            log.info("[HashRing] Task {} hashed to worker {} (actual: {})", task.getId(), assignedByRing, worker.getId());

            // Get the handler for this task type
            TaskHandler handler = handlers.get(task.getType());
            if (handler == null) {
                handleFailure(task, worker, "No handler registered for task type: " + task.getType());
                return;
            }



            TaskAttempt attempt = TaskAttempt.start(task.getId(), task.getRetryCount() + 1, worker.getName());
            // Execute the task
            long startTime = System.currentTimeMillis();
            try {
                String result = handler.execute(task.getId(), task.getPayload());
                long elapsed = System.currentTimeMillis() - startTime;

                // SUCCESS
                task.setStatus(TaskStatus.COMPLETED);
                task.setCompletedAt(LocalDateTime.now());
                task.setResult(result);
                task.setExecutionTimeMs(elapsed);
                taskCompletedCounter.increment();
                taskExecutionTimer.record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);
                task.setErrorMessage(null);
                taskRepository.save(task);

                attempt.complete(result, elapsed);
                taskAttemptRepository.save(attempt);
                completionTimestamps.add(System.currentTimeMillis());

                worker.incrementCompleted();
                worker.setCurrentTaskId(null);
                enqueuedTaskIds.remove(taskId);

                triggerDependents(task.getId());

                if(task.getWorkflowId() != null && workflowService != null){
                    workflowService.updateWorkflowStatus(task.getWorkflowId());
                }

                log.info("Task {} COMPLETED by {} in {}ms", task.getId(), worker.getName(), elapsed);
                TaskEvent completedEvent = TaskEvent.of(task.getId(), task.getName(), TaskStatus.COMPLETED, worker.getName());
                completedEvent.setResult(result);
                completedEvent.setExecutionTimeMs(elapsed);
                kafkaProducerService.publishEvent(completedEvent);

            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                task.setExecutionTimeMs(elapsed);

                attempt.fail(e.getMessage(), elapsed);
                taskAttemptRepository.save(attempt);

                handleFailure(task, worker, e.getMessage());
            }
        }
        finally{
            MDC.remove("taskId");
            MDC.remove("workerName");
        }
        
    }

    private void handleFailure(Task task, WorkerInfo worker, String errorMessage) {
        task.setErrorMessage(errorMessage);
        worker.incrementFailed();
        worker.setCurrentTaskId(null);

        if (retryService.shouldRetry(task)) {
            // Schedule retry with exponential backoff
            task.setRetryCount(task.getRetryCount() + 1);
            task.setStatus(TaskStatus.RETRYING);
            LocalDateTime nextRetry = retryService.getNextRetryTime(task.getRetryCount());
            task.setScheduledAt(nextRetry);
            taskRepository.save(task);

            enqueuedTaskIds.remove(task.getId());
            log.warn("Task {} FAILED (attempt {}/{}). Retrying at {}. Error: {}",
                    task.getId(), task.getRetryCount(), task.getMaxRetries(),
                    nextRetry, errorMessage);

            TaskEvent retryEvent = TaskEvent.of(task.getId(), task.getName(), TaskStatus.RETRYING, worker.getName());
            retryEvent.setErrorMessage(errorMessage);
            retryEvent.setRetryCount(task.getRetryCount());
            retryEvent.setMaxRetries(task.getMaxRetries());
            kafkaProducerService.publishEvent(retryEvent);
        } else {
            // Max retries exhausted - send to Dead Letter Queue
            task.setStatus(TaskStatus.FAILED);
            taskFailedCounter.increment();
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

            enqueuedTaskIds.remove(task.getId());
            log.error("Task {} FAILED permanently after {} attempts. Error: {}",
                    task.getId(), task.getRetryCount(), errorMessage);

            TaskEvent failedEvent = TaskEvent.of(task.getId(), task.getName(), TaskStatus.FAILED, worker.getName());
            failedEvent.setErrorMessage(errorMessage);
            failedEvent.setRetryCount(task.getRetryCount());
            failedEvent.setMaxRetries(task.getMaxRetries());
            kafkaProducerService.publishEvent(failedEvent);
            kafkaProducerService.sendToDlq(task);

            if(task.getWorkflowId() != null && workflowService != null){
                workflowService.updateWorkflowStatus(task.getWorkflowId());
            }
        }
    }

    // ==================== SCHEDULED: Poll for Pending Tasks ====================

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:2000}")
    public void pollPendingTasks() {
        if (!running) return;

        List<Task> pending = taskRepository
                .findByStatusOrderByPriorityAscCreatedAtAsc(TaskStatus.PENDING);

        for (Task task : pending) {

            if(dagService.areDependenciesMet(task.getId())){
                enqueue(task);
            }
            
        }
    }

    // ==================== SCHEDULED: Check Retrying Tasks ====================

    @Scheduled(fixedRate = 3000)
    public void checkRetryingTasks() {
        if (!running) return;

        List<Task> retrying = taskRepository
                .findByStatusAndScheduledAtBefore(TaskStatus.RETRYING, LocalDateTime.now());

        for (Task task : retrying) {
            log.info("Re-enqueueing retrying task {} (attempt {}/{})",
                    task.getId(), task.getRetryCount(), task.getMaxRetries());
            enqueue(task);
        }
    }

    // ==================== SCHEDULED: Heartbeat Monitor ====================

    @Scheduled(fixedDelayString = "${scheduler.heartbeat-interval-ms:5000}")
    public void checkWorkerHealth() {
        LocalDateTime threshold = LocalDateTime.now()
                .minus(heartbeatTimeoutMs, ChronoUnit.MILLIS);

        for (WorkerInfo worker : workers.values()) {
            if (worker.getStatus() != WorkerStatus.OFFLINE
                    && worker.getLastHeartbeat().isBefore(threshold)) {
                log.warn("{} missed heartbeat - marking OFFLINE", worker.getName());
                worker.setStatus(WorkerStatus.OFFLINE);
            }
        }

        // Broadcast stats periodically
        notificationService.broadcastStats(getStats());
    }

    // ==================== SCHEDULED: Stuck Task Revovery ====================

    @Scheduled(fixedDelayString = "${scheduler.stuck-check-interval-ms:5000}")
    public void recoverStuckTasks(){
        if(!running) return;

        List<Task> runningTasks = taskRepository.findByStatus(TaskStatus.RUNNING);
        LocalDateTime now = LocalDateTime.now();

        for(Task task : runningTasks){
            if(task.getStartedAt() == null) continue;

            long elapsedTime = java.time.Duration.between(task.getStartedAt(), now).toMillis();
            long timeout = task.getTimeoutMs() > 0 ? task.getTimeoutMs() : 60000;

            if(elapsedTime > timeout){
                log.warn("[StuckRecovery] Task {} stuck RUNNING for {}ms (timeout={}ms), worker={}",
                        task.getId(), elapsedTime, timeout, task.getAssignedWorker());

                recoveredTaskCount.incrementAndGet();

                if(retryService.shouldRetry(task)){
                    task.setRetryCount(task.getRetryCount() + 1);
                    task.setStatus(TaskStatus.RETRYING);
                    task.setErrorMessage("Task timed out after " +  elapsedTime + "ms (recovered by stuck task monitor)");
                    LocalDateTime nextRetry = retryService.getNextRetryTime(task.getRetryCount());
                    task.setScheduledAt(nextRetry);
                    taskRepository.save(task);

                    log.info("[StuckRecovery] Task {} marked RETRYING (attempt {}/{})",
                        task.getId(), task.getRetryCount(), task.getMaxRetries()
                    );

                    kafkaProducerService.publishEvent(
                        TaskEvent.of(task.getId(), task.getName(), TaskStatus.RETRYING, task.getAssignedWorker())
                    );

                }
                else{
                    task.setStatus(TaskStatus.FAILED);
                    task.setCompletedAt(now);
                    task.setErrorMessage("Task timed out and exhausted all retried (recovered by stuck task monitor)");
                    taskRepository.save(task);

                    log.info("[StuckRecovery] Task {} failed permanently (no retried left)",
                        task.getId()
                    );

                    kafkaProducerService.publishEvent(
                        TaskEvent.of(task.getId(), task.getName(), TaskStatus.FAILED, task.getAssignedWorker())
                    );
                    kafkaProducerService.sendToDlq(task);
                }

                // C;ear the worker's current task reference

                String workerName = task.getAssignedWorker();
                if(workerName != null){
                    workers.values().stream()
                        .filter(w -> workerName.equals(w.getName()))
                        .findFirst()
                        .ifPresent(w-> w.setCurrentTaskId(null));
                }

                notificationService.broadcastTaskUpdate(TaskResponse.from(task));

            }

        }
    }
    
    // ==================== Stats & Info ====================

    public SchedulerStats getStats() {
        SchedulerStats stats = new SchedulerStats();
        stats.setTotalTasks(taskRepository.count());
        stats.setPendingTasks(taskRepository.countByStatus(TaskStatus.PENDING));
        stats.setQueuedTasks(taskRepository.countByStatus(TaskStatus.QUEUED));
        stats.setRunningTasks(taskRepository.countByStatus(TaskStatus.RUNNING));
        stats.setCompletedTasks(taskRepository.countByStatus(TaskStatus.COMPLETED));
        stats.setFailedTasks(taskRepository.countByStatus(TaskStatus.FAILED));
        stats.setRetryingTasks(taskRepository.countByStatus(TaskStatus.RETRYING));
        stats.setQueueSize(taskQueue.size());

        long active = workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.BUSY).count();
        long idle = workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.IDLE).count();
        long offline = workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.OFFLINE).count();

        stats.setActiveWorkers((int) active);
        stats.setIdleWorkers((int) idle);
        stats.setOfflineWorkers((int) offline);

        long oneMinuteAgo = System.currentTimeMillis() - 60_000;
        completionTimestamps.removeIf(ts -> ts < oneMinuteAgo);
        long completedLastMinute = completionTimestamps.size();
        stats.setTaskCompletedLastMinute(completedLastMinute);
        stats.setThroughputPerSecond(Math.round(completedLastMinute / 60.0 * 100.0) / 100.0);

        stats.setHashRingNodeCount(hashRing.getNodeCount());
        stats.setHashRingDistribution(hashRing.getDistribution());

        Map<String, Integer> workerDist =  new LinkedHashMap<>();
        for( WorkerInfo w : workers.values()){
            workerDist.put(w.getName(), w.getTasksCompleted() + w.getTasksFailed());
        }
        stats.setWorkerTaskDistribution(workerDist);

        stats.setTotalWorkers(workers.size());
        stats.setRecoveredTasks(recoveredTaskCount.get());
        stats.setDlqCount(dlqRepository.count());
        stats.setRateLimitRejections(rateLimiterService.getTotalRejections());


        return stats;
    }

    public Collection<WorkerInfo> getWorkers() {
        return workers.values();
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    private void triggerDependents(String completedTaskId){
        List<String> dependents = dagService.getDependents(completedTaskId);

        for(String depId : dependents){
            taskRepository.findById(depId).ifPresent(depTask -> {
                if(depTask.getStatus() == TaskStatus.PENDING && dagService.areDependenciesMet(depId)){
                    log.info("[DAG] Triggering dependent task {} (dependency {} completed)", depId, completedTaskId);
                    enqueue(depTask);
                }
            });
        }
    }

    public ConsistentHashRing getHashRing(){
        return hashRing;
    }

    public boolean isQueueFull() {
        return taskQueue.size() >= maxQueueCapacity;
    }

    public int getMaxQueueCapacity() {
        return maxQueueCapacity;
    }

    
}
