package com.rokkon.pipeline.registration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Event observer that captures events for testing purposes
 */
@ApplicationScoped
public class TestEventObserver {
    
    private static final Logger LOG = Logger.getLogger(TestEventObserver.class);
    
    private final ConcurrentLinkedQueue<TestModuleEvent> capturedEvents = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ModuleRegistrationEvent> registrationEvents = new ConcurrentLinkedQueue<>();
    
    public void onTestModuleEvent(@Observes TestModuleEvent event) {
        LOG.infof("📢 Captured test module event: %s for document %s", event.eventType(), event.documentId());
        capturedEvents.add(event);
    }
    
    public void onModuleRegistrationEvent(@Observes ModuleRegistrationEvent event) {
        LOG.infof("📢 Captured registration event: %s for module %s", event.eventType(), event.moduleName());
        registrationEvents.add(event);
    }
    
    public ConcurrentLinkedQueue<TestModuleEvent> getCapturedEvents() {
        return capturedEvents;
    }
    
    public ConcurrentLinkedQueue<ModuleRegistrationEvent> getRegistrationEvents() {
        return registrationEvents;
    }
    
    public void clearEvents() {
        capturedEvents.clear();
        registrationEvents.clear();
    }
}