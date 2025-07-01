package com.rokkon.pipeline.engine.grpc;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier for ServiceDiscovery implementations.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface ServiceDiscoveryImpl {
    /**
     * The type of service discovery implementation.
     */
    Type value();
    
    enum Type {
        STORK,
        CONSUL_DIRECT
    }
}