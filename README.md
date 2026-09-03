
# Distributed Task Scheduler

A multi-threaded job scheduling system that demonstrates core **distributed systems**, **OS concurrency**, and **software engineering** patterns - built with Spring Boot, Apache Kafka, and a real-time WebSocket dashboard.

**What** - A task scheduler that accepts jobs via REST API, queues them by priority, dispatches to a thread pool of workers, handles failures with retries and circuit breakers, and orchestrates multi-step workflows via DAG dependencies.

**Why** - To implement and showcase production-grade patterns (consistent hashing, backpressure, graceful shutdown, idempotency, starvation prevention, exponential backoff) in a single, runnable project.

**How** - Tasks flow through: `REST API -> Kafka -> PriorityBlockingQueue -> Worker Threads -> TaskHandler`. Results stream back to the browser via WebSocket (STOMP).

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3.2, Spring Data JPA |
| **Messaging** | Apache Kafka (3 topics: submissions, events, DLQ) |
| **Database** | PostgreSQL + Flyway migrations |
| **Caching** | Redis (Spring cache abstraction and districbuted locks) |
| **WebSocket** | STOMP over SockJS |
| **Metrics** | Micrometer + Prometheus + grafana dashboard |
| **Frontend** | Vanilla JS, Chart.js, HTML5 Canvas |
| **Infra** | Docker Compose (App, Postgres, Redis, Kafka, Prometheus, Grafana, Loki, pgAdmin) |

---

## Architecture

```mermaid
flowchart TD
    Client["Client (Browser)"]
    REST["TaskController\n(REST API)"]
    KafkaSub["Kafka: task-submissions"]
    Consumer["KafkaConsumerService"]
    Queue["PriorityBlockingQueue\n(backpressure + age-boosting)"]
    Workers["Worker Thread Pool\n+ Consistent Hash Ring"]
    Handler["TaskHandler (Strategy)\nDATA_PROCESSING, MULTI_CSV, ..."]
    Success["COMPLETED\ntriggerDependents()"]
    Retry["RetryService\n(exp backoff + jitter)"]
    DLQ["Dead Letter Queue\n(Kafka)"]
    Events["Kafka: task-events"]
    WS["WebSocket STOMP"]
    DB["PostgreSQL"]
    Cache["Redis Cache"]

    Client -->|"REST + WebSocket"| REST
    REST --> KafkaSub
    KafkaSub --> Consumer
    Consumer --> Queue
    Queue --> Workers
    Workers --> Handler
    Handler -->|"SUCCESS"| Success
    Handler -->|"FAILURE"| Retry
    Retry -->|"max retries exceeded"| DLQ
    Retry -->|"retry"| Queue
    Success --> Events
    Events --> WS
    WS --> Client
    REST -.->|"read/write"| DB
    REST -.->|"cache"| Cache
```

---

## Project Structure

```text
distributed-task-scheduler/
├── docker/docker-compose.yml       # Kafka + Zookeeper + Kafka UI
├── data/                           # Sample CSV files for task handlers
├── pom.xml
└── src/main/
    ├── java/com/scheduler/
    │   ├── config/                 # Kafka, WebSocket, Metrics, Wiring configs
    │   ├── controller/TaskController # REST API (tasks, workflows, stats, DAG, hash ring)
    │   ├── dto/                    # Request/Response DTOs, Kafka events
    │   ├── handler/                # TaskHandler interface + implementations
    │   ├── model/                  # JPA entities (Task, Workflow, DLQ, Attempt, Dependency)
    │   ├── repository/             # Spring Data repos + JPA Specifications
    │   └── service/                # Core logic (Scheduler, DAG, HashRing, Retry, RateLimiter)
    └── resources/
        ├── application.yml
        └── static/                 # index.html, app.js, styles.css (dashboard)

```

---

## Code Flow

1. User submits task -> POST `/api/tasks`
2. Backpressure check -> queue full? -> 429
3. Rate limit check -> token bucket empty? -> 429
4. Idempotency check -> duplicate key? -> return existing task
5. `TaskService.createTask()` -> persist to H2
6. `KafkaProducerService` -> publish to `task-submissions`
7. `KafkaConsumerService` -> consume -> `SchedulerService.enqueue()`
8. `PriorityBlockingQueue` -> ordered by priority + age boost
9. Worker thread polls -> `executeTask()`
10. `TaskHandler.execute()` -> real CSV processing / simulation
11. Success -> COMPLETED, trigger DAG dependents
Failure -> `RetryService` (backoff) or DLQ
12. Kafka: task-events -> WebSocket STOMP -> dashboard update
 
---
 
### 0. High-Level Overview
 
How the 6 stages below connect to each other.
 
```mermaid
flowchart LR
    A["1. Task Submission<br/>(Client → API → Kafka)"] --> B["2. Consumption & Queueing<br/>(Kafka → Scheduler → Queue)"]
    B --> C["3. Worker Execution<br/>Success Path"]
    B --> D["4. Worker Execution<br/>Failure & Retry Path"]
    C --> E["5. Event Broadcasting<br/>(Kafka → WebSocket → Client)"]
    D --> E
    F["6. Background Scheduled Jobs"] -.polls/recovers.-> B
    F -.recovers stuck tasks.-> D
```
 
---
 
### 1. Task Submission
 
Client hits the API; auth, capacity checks, rate limiting, and idempotency are all resolved before the task is persisted and handed off to Kafka.
 
```mermaid
sequenceDiagram
    actor Client
    participant Auth as JwtAuthFilter
    participant TC as TaskController
    participant SS as SchedulerService
    participant RL as RateLimiterService
    participant TS as TaskService
    participant PG as PostgreSQL
    participant KP as KafkaProducer
    participant Kafka
 
    Client->>Auth: POST /api/tasks (Bearer JWT)
    Auth->>Auth: Validate token, set SecurityContext
    Auth->>TC: Authenticated request
 
    TC->>SS: isQueueFull()?
    alt Queue full
        TC->>Client: 429 Too Many Requests
    end
 
    TC->>RL: tryAcquire(taskType)
    alt Rate limit exceeded
        TC->>Client: 429 Rate Limit
    end
 
    TC->>TS: createTask(request)
    TS->>PG: findByIdempotencyKey()
    alt Duplicate key
        PG->>TS: existing task
        TS->>TC: return existing
    end
    TS->>PG: save(task) -> status=PENDING
    TC->>KP: submitTask(task)
    KP->>Kafka: topic: task-submissions
    TC->>Client: 201 Created
```
 
---
 
### 2. Consumption & Queueing
 
Kafka message is picked up, persisted as QUEUED, and placed on the in-memory priority queue for workers to pick up.
 
```mermaid
sequenceDiagram
    participant Kafka
    participant KC as KafkaConsumer
    participant SS as SchedulerService
    participant PG as PostgreSQL
    participant Queue as PriorityBlockingQueue
    participant KP as KafkaProducer
 
    Kafka->>KC: consume message
    KC->>SS: enqueue(task)
    SS->>PG: save(task) -> status=QUEUED
    SS->>Queue: offer(TaskQueueItem)
    SS->>KP: publishEvent(QUEUED)
```
 
---
 
### 3. Worker Execution — Success Path
 
A worker pulls a task off the queue, checks the circuit breaker, executes it, and on success triggers any dependent tasks via the DAG.
 
```mermaid
sequenceDiagram
    participant Queue as PriorityBlockingQueue
    participant Worker as Worker Thread
    participant PG as PostgreSQL
    participant KP as KafkaProducer
    participant Handler as TaskHandler
    participant DAG as DagService
    participant SS as SchedulerService
    participant WF as WorkflowService
 
    Worker->>Queue: poll(1s timeout)
    Queue->>Worker: TaskQueueItem
    Worker->>PG: findById(taskId)
    Worker->>PG: save(task) -> status=RUNNING
    Worker->>KP: publishEvent(RUNNING)
    Worker->>Handler: execute(taskId, payload)
    Handler-->>Worker: result (success)
 
    Worker->>PG: save(task) -> status=COMPLETED
    Worker->>PG: save(TaskAttempt)
    Worker->>DAG: triggerDependents(taskId)
    DAG->>PG: check downstream dependencies
    DAG->>SS: enqueue(dependent tasks)
    Worker->>WF: updateWorkflowStatus()
    Worker->>KP: publishEvent(COMPLETED)
```
 
---
 
### 4. Worker Execution — Failure & Retry Path
 
Same execution step as above, but the exception branch: retry with backoff, or exhaust retries and route to the dead letter queue.
 
```mermaid
sequenceDiagram
    participant Worker as Worker Thread
    participant Handler as TaskHandler
    participant PG as PostgreSQL
    participant Retry as RetryService
    participant KP as KafkaProducer
    participant DLQ as Dead Letter Queue
    participant WF as WorkflowService
 
    Handler-->>Worker: exception
    Worker->>PG: save(TaskAttempt) -> failed
    Worker->>Retry: shouldRetry(task)?
 
    alt Retries remaining
        Retry-->>Worker: yes
        Worker->>PG: save(task) -> status=RETRYING, scheduledAt=backoff
        Worker->>KP: publishEvent(RETRYING)
    else Max retries exhausted
        Retry-->>Worker: no
        Worker->>PG: save(task) -> status=FAILED
        Worker->>KP: publishEvent(FAILED)
        Worker->>KP: sendToDlq(task)
        KP->>DLQ: topic: task-dlq
        Worker->>WF: updateWorkflowStatus()
    end
```
 
---
 
### 5. Event Broadcasting to Dashboard
 
Every status change published to Kafka's `task-events` topic gets relayed to connected clients over STOMP.
 
```mermaid
sequenceDiagram
    participant KP as KafkaProducer
    participant Kafka
    participant WS as WebSocket STOMP
    actor Client
 
    KP->>Kafka: topic: task-events
    Kafka->>WS: broadcast
    WS->>Client: live update
```
 
---
 
### 6. Background Scheduled Jobs
 
The four `@Scheduled` jobs that keep the system self-healing: promoting ready tasks, retrying due tasks, recovering stuck tasks, and reporting health.
 
```mermaid
sequenceDiagram
    participant SS as SchedulerService
    participant PG as PostgreSQL
    participant DAG as DagService
    participant Queue as PriorityBlockingQueue
    participant Retry as RetryService
    participant WS as WebSocket STOMP
 
    Note over SS,PG: @Scheduled: pollPendingTasks (2s)
    SS->>PG: findByStatus(PENDING)
    SS->>DAG: areDependenciesMet()?
    SS->>Queue: enqueue if ready
 
    Note over SS,PG: @Scheduled: checkRetryingTasks (3s)
    SS->>PG: findByStatus(RETRYING) where scheduledAt < now
    SS->>Queue: re-enqueue for retry
 
    Note over SS,PG: @Scheduled: recoverStuckTasks (5s)
    SS->>PG: findByStatus(RUNNING) where elapsed > timeout
    SS->>Retry: shouldRetry? -> RETRYING or FAILED+DLQ
 
    Note over SS,WS: @Scheduled: checkWorkerHealth (5s)
    SS->>WS: broadcastStats()
```


---

## Quick Local Setup

**Prerequisites**: Java 17+, Maven 3.8+, Docker

```bash
# 1. Start Kafka
cd docker && docker-compose up -d

# 2. Build & Run
cd .. && mvn spring-boot:run

# 3. Open dashboard
open http://localhost:8080

```

| Service | URL | Credentials |
| --- | --- | --- |
| Dashboard | `http://localhost:8080` | JWT Required |
| Grafana | `http://localhost:3000` | admin/admin |
| Kafka UI | `http://localhost:9090` | - |
| Prometheus Metrics | `http://localhost:9091` | - |
| pgAdmin | `http://localhost:5050` | admin@scheduler.com/ admin | 

---

## Features

* **Priority scheduling** - 4 levels (CRITICAL -> LOW), FIFO within same priority
* **DAG workflows** - visual canvas builder with dependency arrows, topological sort, live execution viz
* **Real CSV processing** - sort, filter, aggregate on actual CSV files; multi-file join/merge/compare
* **Retry with exponential backoff** - configurable max retries, jitter to avoid thundering herd
* **Consistent hash ring** - 150 virtual nodes per worker, MD5 hashing, minimal redistribution
* **Rate limiting** - token bucket (global + per-type), configurable capacity and refill rate
* **Backpressure** - rejects submissions (HTTP 429) when queue is at capacity
* **Graceful shutdown** - drains queue back to PENDING, waits 30s for workers to finish
* **Idempotency keys** - duplicate task prevention via unique key on submission
* **Starvation prevention** - age-based priority boost (effective priority increases every 10s in queue)
* **Dead Letter Queue** - permanently failed tasks published to Kafka DLQ topic
* **Prometheus metrics** - counters (submitted/completed/failed), timer (execution time), gauges (queue size, workers)
* **JSON payload builder** - visual form with per-type schemas, conditional fields, live preview
* **Server-side filtering & pagination** - JPA Specifications with dynamic query params
* **PostgreSQL + Flyway** - persistent storage with versioned schema migrations
* **Redis caching + locks** - @Cacheable lookups with TTL, distributed lock service
* **Grafana dashboards** - auto-provisioned: task rates, queue/workers, P50/P95/P99 latency, JVM metrics
* **Structured logging** - JSON logs, correlation IDs via MDC, Loki+Promtail aggregation
* **Spring Security + JWT** - stateless auth, RBAC (ADMIN: write, VIEWER: read-only), BCrypt hashing

---

## Algorithms, OS Concepts & Engineering Patterns

### Algorithms

* **Kahn's Algorithm** - BFS topological sort for DAG execution ordering (`DagService`)
* **DFS 3-color cycle detection** - white/grey/black marking to detect circular dependencies
* **Consistent hashing** - MD5 hash ring with virtual nodes and clockwise lookup (`ConsistentHashRing`)
* **Exponential backoff with jitter** - `delay = base * 2^retryCount + random(0, 25%)` (`RetryService`)
* **Token bucket** - rate limiting with burst capacity and steady refill (`RateLimiterService`)

### OS / Concurrency Concepts

* **Producer-Consumer** - REST API produces -> `PriorityBlockingQueue` -> worker threads consume
* **Thread pool** - `ExecutorService` manages worker lifecycle and task dispatch
* **Priority scheduling** - `Comparable`-based ordering in `PriorityBlockingQueue`
* **Starvation prevention** - age-based priority aging so low-priority tasks eventually execute
* **Backpressure** - bounded queue with rejection when full
* **Graceful shutdown** - `@PreDestroy` drains queue, `awaitTermination` for in-flight tasks
* **Heartbeat / Liveness** - `@Scheduled` polling detects offline workers by timestamp
* **Concurrent data structures** - `ConcurrentHashMap`, `ConcurrentSkipListMap`, `PriorityBlockingQueue`, `AtomicInteger`, `AtomicLong`, `volatile`

### Software Engineering Patterns

* **Strategy** - `TaskHandler` interface with pluggable implementations per task type
* **Observer** - WebSocket STOMP broadcasts state changes to all subscribers
* **Factory** - static factory methods (`TaskResponse.from()`, `TaskEvent.of()`, `TaskAttempt.start()`)
* **Idempotency** - unique key deduplication on task creation
* **Event-driven architecture** - Kafka decouples submission, processing, and notification
* **Dead Letter Queue** - poison message isolation for permanently failed tasks
* **DTO pattern** - request/response separation from JPA entities
* **Dependency Injection** - Spring constructor injection; `List<TaskHandler>` auto-collected
