package com.orchestrator.service;

import com.orchestrator.model.Job;

import java.util.LinkedHashSet;
import java.util.Set;

public class JobSpecParser {

    public Job parse(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Expected id|name|priority|deps but got: " + line);
        }
        int priority = Integer.parseInt(parts[2].trim());
        Set<String> dependencies = new LinkedHashSet<>();
        for (String dependency : parts[3].split(",")) {
            String trimmed = dependency.trim();
            if (!trimmed.isEmpty()) {
                dependencies.add(trimmed);
            }
        }
        return new Job(parts[0].trim(), parts[1].trim(), priority, dependencies);
    }
}
