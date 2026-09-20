package com.orchestrator.service;

@FunctionalInterface
public interface JobTask {
    void run() throws Exception;
}
