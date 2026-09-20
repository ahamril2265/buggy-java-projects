package com.orchestrator.service;

import com.orchestrator.model.Job;

import java.util.PriorityQueue;

public class PriorityJobQueue {
    private final PriorityQueue<Job> queue = new PriorityQueue<>(PriorityJobQueue::compare);
    private long nextSequence = 0;

    public void submit(Job job) {
        job.setSequence(nextSequence++);
        queue.add(job);
    }

    public Job poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }

    private static int compare(Job a, Job b) {
        int byPriority = Integer.compare(b.getPriority() , a.getPriority());
        if (byPriority != 0) {
            return byPriority;
        }
        return Long.compare(a.getSequence(), b.getSequence());
    }
}
