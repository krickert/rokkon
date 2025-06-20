import com.google.protobuf.InvalidProtocolBufferException;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessResponse;
import com.rokkon.search.sdk.ProcessRequest;

import java.io.FileInputStream;
import java.io.IOException;

public class CheckSampleType {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java CheckSampleType <file.bin>");
            return;
        }
        
        String filename = args[0];
        byte[] data;
        
        try (FileInputStream fis = new FileInputStream(filename)) {
            data = fis.readAllBytes();
        }
        
        // Try PipeStream
        try {
            PipeStream stream = PipeStream.parseFrom(data);
            System.out.println("File is a PipeStream!");
            System.out.println("- Stream ID: " + stream.getStreamId());
            System.out.println("- Has Document: " + stream.hasDocument());
            if (stream.hasDocument()) {
                System.out.println("  - Doc ID: " + stream.getDocument().getId());
                System.out.println("  - Has Body: " + stream.getDocument().hasBody());
                System.out.println("  - Body Length: " + (stream.getDocument().hasBody() ? stream.getDocument().getBody().length() : 0));
            }
            return;
        } catch (InvalidProtocolBufferException e) {
            // Not a PipeStream
        }
        
        // Try ProcessResponse
        try {
            ProcessResponse response = ProcessResponse.parseFrom(data);
            System.out.println("File is a ProcessResponse!");
            System.out.println("- Success: " + response.getSuccess());
            System.out.println("- Has Output Doc: " + response.hasOutputDoc());
            if (response.hasOutputDoc()) {
                System.out.println("  - Doc ID: " + response.getOutputDoc().getId());
                System.out.println("  - Has Body: " + response.getOutputDoc().hasBody());
                System.out.println("  - Body Length: " + (response.getOutputDoc().hasBody() ? response.getOutputDoc().getBody().length() : 0));
            }
            return;
        } catch (InvalidProtocolBufferException e) {
            // Not a ProcessResponse
        }
        
        // Try ProcessRequest
        try {
            ProcessRequest request = ProcessRequest.parseFrom(data);
            System.out.println("File is a ProcessRequest!");
            System.out.println("- Has Document: " + request.hasDocument());
            if (request.hasDocument()) {
                System.out.println("  - Doc ID: " + request.getDocument().getId());
                System.out.println("  - Has Body: " + request.getDocument().hasBody());
                System.out.println("  - Body Length: " + (request.getDocument().hasBody() ? request.getDocument().getBody().length() : 0));
            }
            return;
        } catch (InvalidProtocolBufferException e) {
            // Not a ProcessRequest
        }
        
        System.out.println("Could not determine protobuf type!");
    }
}