package com.scheduler.config;

import com.scheduler.service.SchedulerService;
import com.scheduler.service.WorkflowService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowWiringConfig {

    private final SchedulerService schedulerService;
    private final WorkflowService workflowService;

    public WorkflowWiringConfig(SchedulerService schedulerService, WorkflowService workflowService) {
        this.schedulerService = schedulerService;
        this.workflowService = workflowService;
    }

    @PostConstruct
    public void wire() {
        schedulerService.setWorkflowService(workflowService);
    }
}
