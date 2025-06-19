package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.search.model.PipeStream;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation of KafkaRouter for initial development.
 * TODO: Implement actual Kafka integration when Kafka support is added.
 */
@ApplicationScoped
public class KafkaRouterStub implements KafkaRouter {
    
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRouterStub.class);
    
    @Override
    public Uni<Void> routeToModule(String pipelineName, String targetStepName, 
                                  PipeStream stream, PipelineStepConfig stepConfig) {
        LOG.warn("Kafka routing not yet implemented. Would route stream {} to {}.{}", 
                stream.getStreamId(), pipelineName, targetStepName);
        
        // For now, just complete successfully
        return Uni.createFrom().voidItem();
    }
}