package com.orchestrator.service;

import com.orchestrator.model.Job;
import com.orchestrator.model.JobEvent;
import com.orchestrator.model.JobStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Scheduler {
    private final Map<String, Job> jobs;
    private final RetryPolicy retryPolicy;
    private final JobMetrics metrics;
    private final EventBus eventBus;
    private final List<String> executionOrder = new ArrayList<>();
    private final List<Integer> retryDelaysMs = new ArrayList<>();

    public Scheduler(Map<String, Job> jobs, RetryPolicy retryPolicy, JobMetrics metrics, EventBus eventBus) {
        this.jobs = jobs;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.eventBus = eventBus;
    }

    public List<Job> readyJobs() {
        List<Job> ready = new ArrayList<>();
        for (Job job : jobs.values()) {
            if (job.getStatus() == JobStatus.PENDING && isReady(job)) {
                ready.add(job);
            }
        }
        return ready;
    }

    private boolean isReady(Job job) {
        if (job.getDependencies().isEmpty()) {
            return true;
        }
        return job.getDependencies().stream().allMatch(id -> {
            Job dependency = jobs.get(id);
            return dependency != null && dependency.getStatus() == JobStatus.COMPLETED;
        });
    }

    public void runAll(Map<String, JobTask> tasks) {
        while (true) {
            List<Job> ready = readyJobs();
            if (ready.isEmpty()) {
                break;
            }
            ready.sort((a, b) -> {
                int byPriority = Integer.compare(b.getPriority(), a.getPriority());
                return byPriority != 0 ? byPriority : a.getId().compareTo(b.getId());
            });
            Job next = ready.get(0);
            execute(next, tasks.get(next.getId()));
        }
    }

    private void execute(Job job, JobTask task) {
        executionOrder.add(job.getId());
        job.setStatus(JobStatus.RUNNING);
        while (true) {
            job.incrementAttempts();
            try {
                task.run();
                job.setStatus(JobStatus.COMPLETED);
                metrics.recordCompleted();
                eventBus.publish(new JobEvent(job.getId(), JobStatus.COMPLETED));
                return;
            } catch (Exception e) {
                if (retryPolicy.shouldRetry(job.getAttempts())) {
                    retryDelaysMs.add(retryPolicy.delayForRetry(job.getAttempts()));
                    job.setStatus(JobStatus.RETRYING);
                    continue;
                }
                job.setStatus(JobStatus.FAILED);
                metrics.recordFailed();
                eventBus.publish(new JobEvent(job.getId(), JobStatus.FAILED));
                markDependentsSkipped(job);
                return;
            }
        }
    }

    private void markDependentsSkipped(Job failed) {
        for (Job job : jobs.values()) {
            if (job.getStatus() == JobStatus.PENDING && job.getDependencies().contains(failed.getId())) {
                job.setStatus(JobStatus.SKIPPED);
                eventBus.publish(new JobEvent(job.getId(), JobStatus.SKIPPED));
                markDependentsSkipped(job);
            }
        }
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public List<Integer> getRetryDelaysMs() {
        return retryDelaysMs;
    }
}
