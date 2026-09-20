package com.orchestrator.service;

import com.orchestrator.model.Job;
import com.orchestrator.model.JobStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.ArrayList;

public class JobReport {

    public TreeSet<Job> sortedByPriority(Collection<Job> jobs) {
        return new TreeSet<>(jobs);
    }

    public Map<JobStatus, List<String>> namesByStatus(Collection<Job> jobs) {
        return jobs.stream()
            .sorted(Comparator.comparing(Job::getName))
            .collect(Collectors.groupingBy(
                Job::getStatus,
                Collectors.mapping(Job::getName, Collectors.toList())
            ));
    }
}
