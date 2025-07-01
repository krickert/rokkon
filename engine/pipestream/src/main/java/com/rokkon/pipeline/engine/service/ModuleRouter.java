package com.rokkon.pipeline.engine.service;

import com.rokkon.pipeline.config.model.PipelineStepConfig;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ProcessResponse;
import io.smallrye.mutiny.Uni;

/**
 * Interface for routing requests to modules based on transport configuration.
 * Implementations handle the actual communication with modules via gRPC or other transports.
 */
public interface ModuleRouter {
    
    /**
     * Route a process request to the appropriate module based on the step configuration.
     * 
     * @param request The process request to send to the module
     * @param stepConfig The step configuration containing transport and module info
     * @return A Uni containing the process response from the module
     */
    Uni<ProcessResponse> routeToModule(ProcessRequest request, PipelineStepConfig stepConfig);
}