package com.rokkon.pipeline.events.cache;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ModuleRegistration;
import java.util.Set;

/**
 * CDI event fired when Consul state has been restored on engine startup.
 * This indicates that the engine has successfully loaded its state from Consul.
 */
public record ConsulStateRestored(
    Set<ModuleRegistration> restoredModules
) {}