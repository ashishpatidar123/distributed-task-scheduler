CREATE TABLE tasks (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(50) NOT NULL,
    payload         TEXT,
    priority        VARCHAR(20) NOT NULL,
    status   VARCHAR(20) NOT NULL,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    created_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    assigned_worker VARCHAR(255),
    error_message TEXT,
    result TEXT,
    execution_time_ms BIGINT DEFAULT 0,
    timeout_ms BIGINT DEFAULT 60000,
    workflow_id VARCHAR(36),
    idempotent_key VARCHAR(255) UNIQUE

);

CREATE INDEX idx_task_status ON tasks(status);
CREATE INDEX idx_task_status_priority ON tasks(status, priority);
CREATE INDEX idx_task_workflow ON tasks(workflow_id);
CREATE INDEX idx_task_created ON tasks(created_at);

CREATE TABLE task_attempts (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    attempt_number  INT DEFAULT 1,
    worker_name    VARCHAR(255),
    result_status     VARCHAR(20),
    started_at      TIMESTAMP,
    ended_at        TIMESTAMP,
    duration_ms     BIGINT DEFAULT 0,
    error_message   TEXT,
    result          TEXT
);  

CREATE INDEX idx_attempt_task ON task_attempts(task_id);

CREATE TABLE task_dependencies(
    id             BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    depends_on_task_id VARCHAR(36) NOT NULL
);

CREATE INDEX idx_dep_task ON task_dependencies(task_id);
CREATE INDEX idx_dep_depends ON task_dependencies(depends_on_task_id);

CREATE TABLE dlq_entries(
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    task_name       VARCHAR(255),
    task_type       VARCHAR(50),
    payload         TEXT,
    priority        VARCHAR(20),
    error_message   TEXT,
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    worker_name      VARCHAR(255),
    failed_at      TIMESTAMP,
    replayed BOOLEAN DEFAULT FALSE,
    replayed_at TIMESTAMP
);

CREATE TABLE workflows (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL,
    total_tasks     INT DEFAULT 0,
    completed_tasks INT DEFAULT 0,
    failed_tasks    INT DEFAULT 0,
    created_at      TIMESTAMP,
    completed_at    TIMESTAMP
);
