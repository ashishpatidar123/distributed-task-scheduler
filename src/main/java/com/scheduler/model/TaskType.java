package com.scheduler.model;

// types of tasks, currently only 2, can advance to sql or pyscript based tasks too
public enum TaskType{
    
    DATA_PROCESSING,
    MULTI_CSV_PROCESSING
    
}