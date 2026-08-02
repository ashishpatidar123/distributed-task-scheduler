package com.scheduler.dto;

import java.util.List;

// defining this DTO to validate a workflow, like it should not contain any cycle, since it's DAG

public class DagValidationResult {

    private boolean valid;
    private List<String> cycle;
    private List<String> topologicalOrder;
    private String message;

    public DagValidationResult(){}

    // if valid then only send it for execution
    public static DagValidationResult valid(List<String> order){
        DagValidationResult r = new DagValidationResult();
        r.valid = true;
        r.topologicalOrder = order;
        r.message = "Dag is valid. Execution order: " + order;
        return r;
    }

    // not valid then throw error
    public static DagValidationResult invalid(List<String> cycle){
        DagValidationResult r = new DagValidationResult();
        r.valid = false;
        r.topologicalOrder = cycle;
        r.message = "Cycle detected: " + cycle;
        return r;
    }

    public boolean isValid(){
        return valid;
    }
    public List<String> getCycle(){
        return cycle;
    }
    public List<String> getTopologicalOrder(){
        return topologicalOrder;
    }
    public String getMessage(){
        return message;
    }


    
}
