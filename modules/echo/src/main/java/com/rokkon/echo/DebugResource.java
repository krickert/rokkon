package com.rokkon.echo;

import com.rokkon.search.model.PipeStream;
import com.rokkon.search.util.SampleDataLoader;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Path("/api/echo/debug")
@Produces(MediaType.APPLICATION_JSON)
public class DebugResource {
    
    private static final Logger LOG = Logger.getLogger(DebugResource.class);
    
    @GET
    @Path("/sample/{number}")
    public Response debugSample(@PathParam("number") int number) {
        try {
            String filename = "sample-pipestream-" + number + ".bin";
            PipeStream stream = SampleDataLoader.loadSamplePipeStream(filename);
            
            Map<String, Object> result = new HashMap<>();
            result.put("filename", filename);
            result.put("hasDocument", stream.hasDocument());
            result.put("streamId", stream.getStreamId());
            result.put("currentPipelineName", stream.getCurrentPipelineName());
            result.put("targetStepName", stream.getTargetStepName());
            result.put("currentHopNumber", stream.getCurrentHopNumber());
            result.put("historyCount", stream.getHistoryCount());
            
            if (stream.hasDocument()) {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("id", stream.getDocument().getId());
                docInfo.put("hasBody", stream.getDocument().hasBody());
                docInfo.put("bodyLength", stream.getDocument().hasBody() ? stream.getDocument().getBody().length() : 0);
                docInfo.put("hasTitle", stream.getDocument().hasTitle());
                docInfo.put("titleLength", stream.getDocument().hasTitle() ? stream.getDocument().getTitle().length() : 0);
                docInfo.put("documentType", stream.getDocument().hasDocumentType() ? stream.getDocument().getDocumentType() : null);
                result.put("document", docInfo);
            }
            
            return Response.ok(result).build();
            
        } catch (IOException e) {
            LOG.error("Failed to load sample", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}