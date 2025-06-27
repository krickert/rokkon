package com.rokkon.pipeline.validation;

/**
 * Legacy interface for validators - use ConfigValidator instead.
 * This interface is deprecated and will be removed in a future version.
 * 
 * @param <T> The type of object being validated
 * @deprecated Use {@link ConfigValidator} instead
 */
@Deprecated
public interface Validator<T extends ValidationCapableConfig> extends ConfigValidator<T> {
}
