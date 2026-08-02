package com.scheduler.service;

import com.scheduler.model.TaskType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationConfigService {

    private final ConcurrentHashMap<TaskType, Integer> failureRates = new ConcurrentHashMap<>();

    

    @Value("${scheduler.failure-rates.data-processing:15}")
    private int dataProcessingRate;

    @Value("${scheduler.failure-rates.multi-csv-processing:20}")
    private int multiCsvProcessingRate;

   

    @PostConstruct
    public void init() {
        
        failureRates.put(TaskType.DATA_PROCESSING, dataProcessingRate);
        failureRates.put(TaskType.MULTI_CSV_PROCESSING, multiCsvProcessingRate);
        
    }

    public int getFailureRate(TaskType type) {
        return failureRates.getOrDefault(type, 10);
    }

    public void setFailureRate(TaskType type, int rate) {
        failureRates.put(type, Math.max(0, Math.min(100, rate)));
    }

    public Map<String, Integer> getAllFailureRates() {
        Map<String, Integer> rates = new LinkedHashMap<>();
        for (TaskType type : TaskType.values()) {
            rates.put(type.name(), getFailureRate(type));
        }
        return rates;
    }

    public void updateAllFailureRates(Map<String, Integer> rates) {
        rates.forEach((typeName, rate) -> {
            try {
                TaskType type = TaskType.valueOf(typeName);
                setFailureRate(type, rate);
            } catch (IllegalArgumentException ignored) {}
        });
    }
}
