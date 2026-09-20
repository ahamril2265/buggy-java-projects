package com.orchestrator.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public class Job implements Comparable<Job> {
    private final String id;
    private final String name;
    private final Integer priority;
    private final Set<String> dependencies;
    private JobStatus status = JobStatus.PENDING;
    private int attempts;
    private long sequence;

    public Job(String id, String name, int priority, Set<String> dependencies) {
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.dependencies = new HashSet<>(dependencies);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getPriority() {
        return priority;
    }

    public Set<String> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        attempts++;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public boolean hasSamePriorityAs(Job other) {
        return this.priority.equals(other.priority);
    }

    @Override
    public int compareTo(Job other) {
        int byPriority = Integer.compare(other.priority, this.priority);
        return byPriority != 0 ? byPriority : this.id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Job)) {
            return false;
        }
        return id.equals(((Job) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Job{id=%s, name=%s, priority=%d, status=%s}", id, name, priority, status);
    }
}
