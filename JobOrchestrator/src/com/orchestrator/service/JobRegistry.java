package com.orchestrator.service;

import com.orchestrator.model.Job;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class JobRegistry {
    private final Map<String, Job> jobsById = new LinkedHashMap<>();

    public void register(Job job) {
        jobsById.put(job.getId(), job);
    }

    public Optional<Job> findByName(String name) {
        for (Map.Entry<String, Job> entry : jobsById.entrySet()) {
            Job val = entry.getValue();
            if (val.getName().equals(name)) {
                return Optional.of(val);
            }
        }
        return Optional.empty();
    }
}
