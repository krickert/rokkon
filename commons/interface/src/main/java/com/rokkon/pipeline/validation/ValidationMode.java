package com.rokkon.pipeline.validation;

/**
 * Defines the validation mode for pipeline configurations.
 * Different modes apply different levels of validation strictness.
 */
public enum ValidationMode {
    /**
     * Production mode - applies full validation suite.
     * Requires pipelines to pass all validations without errors or warnings.
     * Suitable for pipelines ready for deployment.
     */
    PRODUCTION,
    
    /**
     * Design mode - applies structural and logical validation.
     * Allows saving pipelines that may have warnings.
     * Suitable for design-time pipeline definitions.
     */
    DESIGN,
    
    /**
     * Testing mode - applies minimal validation.
     * Allows incomplete pipelines for testing purposes.
     * Suitable for test environments.
     */
    TESTING;
    
    /**
     * Checks if this is production mode.
     * @return true if this is PRODUCTION mode
     */
    public boolean isProduction() {
        return this == PRODUCTION;
    }
    
    /**
     * Checks if this is design mode.
     * @return true if this is DESIGN mode
     */
    public boolean isDesign() {
        return this == DESIGN;
    }
    
    /**
     * Checks if this is testing mode.
     * @return true if this is TESTING mode
     */
    public boolean isTesting() {
        return this == TESTING;
    }
}