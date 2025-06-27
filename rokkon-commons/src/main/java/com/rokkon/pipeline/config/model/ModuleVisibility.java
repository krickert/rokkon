package com.rokkon.pipeline.config.model;

/**
 * Defines the visibility scope for registered modules.
 */
public enum ModuleVisibility {
    /**
     * Module is visible in all UIs and can be used by any pipeline.
     */
    PUBLIC,
    
    /**
     * Module is hidden from global UI but can be used in pipelines by those with access.
     * Useful for business-line specific modules or experimental features.
     */
    PRIVATE,
    
    /**
     * Module is restricted to specific business lines or teams.
     * Requires additional authorization checks.
     */
    RESTRICTED;
    
    /**
     * Default visibility for new modules if not specified.
     */
    public static final ModuleVisibility DEFAULT = PUBLIC;
}