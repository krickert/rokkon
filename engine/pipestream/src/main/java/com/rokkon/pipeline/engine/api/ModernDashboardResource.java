package com.rokkon.pipeline.engine.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.InputStream;

@Path("/dashboard/modern")
@Tag(name = "Modern Dashboard", description = "Modern UI dashboard endpoint")
public class ModernDashboardResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Get modern dashboard UI", 
               description = "Returns the modern dashboard UI built with web-bundler")
    public Response getModernDashboard() {
        // The web-bundler template is in the web directory
        InputStream htmlStream = getClass().getResourceAsStream("/web/web-bundler.html");
        if (htmlStream == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("Modern dashboard not found - web-bundler.html not in classpath")
                .build();
        }
        return Response.ok(htmlStream, MediaType.TEXT_HTML).build();
    }
}