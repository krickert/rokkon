package com.rokkon.pipeline.consul.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as requiring MockClusterService instead of the real implementation.
 * Used by UnifiedTestProfile to configure the test environment.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresMockCluster {
}