package com.scheduler.repository;

import com.scheduler.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String>{

    // utilities to get workflows

    List<Workflow> findAllByOrderByCreatedAtDesc();
    List<Workflow> findByStatus(WorkflowStatus status);
    
}
