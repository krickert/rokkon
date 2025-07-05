package com.rokkon.pipeline.engine.dev;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Clears the static Docker client map on shutdown to allow fresh clients after reload.
 * This is a workaround for the Quarkus Docker client extension not handling dev mode reloads.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DockerClientRefresher {
    
    private static final Logger LOG = Logger.getLogger(DockerClientRefresher.class);
    
    void onShutdown(@Observes ShutdownEvent ev) {
        try {
            // Use reflection to access the private static map in DockerClientRecorder
            Class<?> recorderClass = Class.forName("io.quarkiverse.docker.client.runtime.DockerClientRecorder");
            Field clientsField = recorderClass.getDeclaredField("clients");
            clientsField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> clients = (Map<String, Object>) clientsField.get(null);
            
            if (clients != null && !clients.isEmpty()) {
                LOG.info("Clearing Docker client cache for dev mode reload");
                clients.clear();
            }
        } catch (Exception e) {
            LOG.debug("Could not clear Docker client cache (this is okay if the extension changed): " + e.getMessage());
        }
    }
}