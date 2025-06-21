package com.rokkon.test.consul;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Consul KV API
 */
@RegisterRestClient(configKey = "consul-api")
@Path("/v1/kv")
public interface ConsulKVClient {
    
    @GET
    @Path("/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getValue(@PathParam("key") String key, @QueryParam("raw") boolean raw);
    
    @PUT
    @Path("/{key}")
    @Consumes(MediaType.TEXT_PLAIN)
    Response putValue(@PathParam("key") String key, String value);
    
    @DELETE
    @Path("/{key}")
    Response deleteValue(@PathParam("key") String key);
    
    @GET
    @Path("/{prefix}")
    @Produces(MediaType.APPLICATION_JSON)
    Response listKeys(@PathParam("prefix") String prefix, @QueryParam("keys") boolean keys);
}