package com.rokkon.pipeline.engine.config;

import io.smallrye.stork.Stork;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class StorkConfiguration {
    
    @Produces
    @Singleton
    public Stork stork() {
        return Stork.getInstance();
    }
}