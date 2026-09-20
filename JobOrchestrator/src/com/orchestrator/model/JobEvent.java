package com.orchestrator.model;

public record JobEvent(String jobId, JobStatus status) {
}
