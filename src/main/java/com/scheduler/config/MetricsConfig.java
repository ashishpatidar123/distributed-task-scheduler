package com.scheduler.config;

import org.springframework.context.annotation.Configuration;

import com.scheduler.service.SchedulerService;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

// metrics registry for the prometheus dashboard

@Configuration
public class MetricsConfig {

    private final MeterRegistry registry;
    private final SchedulerService schedulerService;

    public MetricsConfig(MeterRegistry registry, SchedulerService schedulerService) {
        this.registry = registry;
        this.schedulerService = schedulerService;
    }

    @PostConstruct
    public void registerGauges(){
        registry.gauge("scheduler.queue.size", schedulerService, SchedulerService::getQueueSize);
        registry.gauge("scheduler.queue.capacity", schedulerService, SchedulerService::getMaxQueueCapacity);
        registry.gauge("scheduler.workers.total", schedulerService, s -> s.getWorkers().size());
        registry.gauge("scheduler.workers.busy", schedulerService,
            s -> s.getWorkers().stream()
                .filter(w -> w.getStatus() == com.scheduler.model.WorkerStatus.BUSY)
                .count()
        );

    }
    
}
