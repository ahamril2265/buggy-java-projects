package com.orchestrator.service;

import com.orchestrator.model.Job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DependencyResolver {

    public List<String> resolveOrder(Map<String, Job> jobs) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inProgress = new HashSet<>();
        for (String id : new TreeSet<>(jobs.keySet())) {
            if (!visited.contains(id)) {
                visit(id, jobs, visited, order, inProgress);
            }
        }
        return order;
    }

    private void visit(String id, Map<String, Job> jobs, Set<String> visited, List<String> order, Set<String> inProgress) {
        if (visited.contains(id)) {
            return;
        }
        if (inProgress.contains(id)) {
            throw new IllegalStateException("Dependency cycle detected at: " + id);
        }
        Job job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("Unknown dependency: " + id);
        }
        inProgress.add(id);
        for (String dependencyId : new TreeSet<>(job.getDependencies())) {
            visit(dependencyId, jobs, visited, order, inProgress);
        }
        inProgress.remove(id);
        visited.add(id);
        order.add(id);
    }
}
