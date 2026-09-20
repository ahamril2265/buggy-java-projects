package com.orchestrator.service;

import com.orchestrator.model.JobEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventBus {
    private final List<Consumer<JobEvent>> listeners = new ArrayList<>();

    public void subscribe(Consumer<JobEvent> listener) {
        listeners.add(listener);
    }

    public List<String> publish(JobEvent event) {
        List<String> errors = new ArrayList<>();
        for (Consumer<JobEvent> listener : listeners) {
            try {
                listener.accept(event);
            } 
            catch (Exception e) {
                errors.add( "Listener failed: " + e.getMessage() );
            }
        }
        return errors;
    }
}
