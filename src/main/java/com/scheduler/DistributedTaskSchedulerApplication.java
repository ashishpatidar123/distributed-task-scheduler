package com.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DistributedTaskSchedulerApplication{
    public static void main(String[] args){
        SpringApplication.run(DistributedTaskSchedulerApplication.class, args);
    }
}
