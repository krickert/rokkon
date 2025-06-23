package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.connection.ConsulConnectionManager;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.ConsulClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Base class for services that need to interact with Consul.
 * Provides a unified way to get a Consul client.
 */
public abstract class AbstractConsulService {

    @Inject
    protected ConsulConnectionManager connectionManager;

    /**
     * Gets the Consul client, failing if not connected.
     *
     * @return A Uni that provides the ConsulClient or fails with a standard error response.
     */
    protected Uni<ConsulClient> getConsulClient() {
        return Uni.createFrom().optional(connectionManager.getClient())
                .onItem().ifNull().failWith(() -> new WebApplicationException(
                        "Not connected to Consul. Please connect via the API.",
                        Response.Status.SERVICE_UNAVAILABLE
                ));
    }
}