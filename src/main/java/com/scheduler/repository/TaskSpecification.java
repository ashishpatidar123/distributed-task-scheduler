package com.scheduler.repository;

import org.springframework.data.jpa.domain.Specification;

import com.scheduler.model.*;

// creating a Specification for filtering and pagination on the UI

public class TaskSpecification {

    public static Specification<Task> hasStatus(String status) {
        return (root, query, criteriaBuilder) -> 
                    status == null || status.isEmpty() ? null :
                        criteriaBuilder.equal(root.get("status"), TaskStatus.valueOf(status));
    }

    public static Specification<Task> hasPriority(String priority) {
        return (root, query, criteriaBuilder) -> 
                    priority == null || priority.isEmpty() ? null :
                        criteriaBuilder.equal(root.get("priority"), TaskPriority.valueOf(priority));
    }
    public static Specification<Task> hasType(String type) {
        return (root, query, criteriaBuilder) -> 
                    type == null || type.isEmpty() ? null :
                        criteriaBuilder.equal(root.get("type"), TaskType.valueOf(type));
    }
    public static Specification<Task> nameLike(String name) {
        return (root, query, criteriaBuilder) -> 
                    name == null || name.isEmpty() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
    public static Specification<Task> idLike(String id) {
        return (root, query, criteriaBuilder) -> 
                    id == null || id.isEmpty() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("id")), "%" + id.toLowerCase() + "%");
    }
    public static Specification<Task> workerLike(String worker) {
        return (root, query, criteriaBuilder) -> 
                    worker == null || worker.isEmpty() ? null :
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("assignedWorker")), "%" + worker.toLowerCase() + "%");
    }
    
}
